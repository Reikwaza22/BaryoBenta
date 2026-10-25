#!/usr/bin/env bash
# =====================================================================
# Seeds browsable demo content through the mobile (PHP-compatible) API:
# six listings with photos and one sample buyer/seller conversation.
#
# Photos are uploaded through listing_create.php, which stores them as
# blobs in media_blob, so this cannot be done from SQL alone.
#
# Usage:
#   ./seed_demo_content.sh [api-base-url]
#   ./seed_demo_content.sh https://baryo-benta.vercel.app
#
# Requires migrations 002 and 003 to have been applied first. Safe to
# re-run: listings that already exist are skipped.
# =====================================================================
set -euo pipefail

API="${1:-https://baryo-benta.vercel.app}"
API="${API%/}"
PHOTOS="$(cd "$(dirname "$0")" && pwd)/demo_photos"

field() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }

echo "Logging in with the demo accounts..."
STOK=$(curl -s -X POST "$API/login.php" --data-urlencode "login=seller@baryobenta.demo" --data-urlencode "password=sellerpass1" | field "['data']['token']")
BTOK=$(curl -s -X POST "$API/login.php" --data-urlencode "login=buyer@baryobenta.demo" --data-urlencode "password=buyerpass1" | field "['data']['token']")
[ -n "$STOK" ] && [ -n "$BTOK" ] || { echo "login failed - are migrations 002/003 applied?"; exit 1; }

listing_exists() {
  curl -s -H "X-Auth-Token: $STOK" "$API/listings.php?mine=1" \
    | python3 -c "import sys,json;print(sum(1 for l in json.load(sys.stdin)['data'] if l['title']==sys.argv[1]))" "$1"
}

post_listing() { # $1 photo  $2 title  $3 category_id  $4 price  $5 description  $6 specs-json
  if [ "$(listing_exists "$2")" != "0" ]; then echo "skip: $2"; return; fi
  curl -s -X POST "$API/listing_create.php" -H "X-Auth-Token: $STOK" \
    -F "photo=@$PHOTOS/$1;type=image/jpeg" -F "title=$2" -F "category_id=$3" \
    -F "price=$4" -F "description=$5" -F "specs=$6" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print('posted:',d['message'] if d['success'] else d['message'])"
}

echo "Posting demo listings..."
post_listing headphones.jpg "Sony WH-1000XM4 Wireless Headphones" 1 6500 \
  "Noise-cancelling over-ear headphones, 9/10 condition. Comes with the original case and charging cable. Battery still lasts around 25 hours." \
  '[{"name":"Brand","value":"Sony"},{"name":"Condition","value":"9/10"},{"name":"Includes","value":"Case, USB-C cable"}]'
post_listing sneakers.jpg "White Canvas Sneakers (Size 9)" 2 850 \
  "Barely worn canvas sneakers, size 9. Cleaned and ready for a new owner. Meet-up only around the campus gate." \
  '[{"name":"Size","value":"9"},{"name":"Condition","value":"Like new"}]'
post_listing desk_fan.jpg "Kyowa 12-inch Desk Fan" 3 700 \
  "Quiet desk fan with three speeds, perfect for the dorm. Works perfectly, selling because I moved to an aircon room." \
  '[{"name":"Brand","value":"Kyowa"},{"name":"Condition","value":"Good"}]'
post_listing basketball.jpg "Molten Basketball Size 7" 4 1200 \
  "Official size 7 ball, good grip, used for one semester of intramurals. Great for practice or barangay games." \
  '[{"name":"Brand","value":"Molten"},{"name":"Size","value":"7"}]'
post_listing car.jpg "2022 Ford Mustang Mach-E (For Sale by Owner)" 5 2350000 \
  "Well-kept electric Mustang Mach-E, single owner, complete papers and casa maintenance record. Serious buyers only; viewing by appointment." \
  '[{"name":"Year","value":"2022"},{"name":"Mileage","value":"18,000 km"},{"name":"Transmission","value":"Automatic"}]'
post_listing textbooks.jpg "IT First-Year Textbook Bundle" 6 1500 \
  "Bundle of five first-year IT textbooks and reviewers, no writing inside. Perfect for incoming freshmen." \
  '[{"name":"Subjects","value":"Programming, Math, COM1"},{"name":"Condition","value":"Clean"}]'

echo "Creating the sample conversation..."
LID=$(curl -s -H "X-Auth-Token: $BTOK" "$API/listings.php?q=Sony%20WH-1000XM4" | field "['data'][0]['listing_id']")
SID=$(curl -s -H "X-Auth-Token: $BTOK" "$API/listing.php?id=$LID" | field "['data']['seller_id']")
BID=$(curl -s -H "X-Auth-Token: $BTOK" "$API/profile.php" | field "['data']['user_id']")

COUNT=$(curl -s -H "X-Auth-Token: $BTOK" "$API/messages.php?listing_id=$LID&other_id=$SID" \
  | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
if [ "$COUNT" != "0" ]; then
  echo "conversation already exists, skipping"
else
  curl -s -X POST "$API/messages.php" -H "X-Auth-Token: $BTOK" \
    -d "listing_id=$LID" -d "other_id=$SID" --data-urlencode "body=Hi po! Available pa ba yung headphones?" > /dev/null
  curl -s -X POST "$API/messages.php" -H "X-Auth-Token: $STOK" \
    -d "listing_id=$LID" -d "other_id=$BID" --data-urlencode "body=Yes! Meet at the campus gate anytime after 4pm." > /dev/null
  curl -s -X POST "$API/messages.php" -H "X-Auth-Token: $BTOK" \
    -d "listing_id=$LID" -d "other_id=$SID" --data-urlencode "body=Sige po, see you tomorrow. Thank you!" > /dev/null
  echo "conversation created on listing $LID"
fi

echo "Done. Demo content is live at $API"
