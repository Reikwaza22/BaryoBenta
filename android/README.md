# Community Buy & Sell Marketplace (BaryoBenta) — Full App UI

Java + XML, Material Design, no Jetpack Compose/Kotlin. Built from the
low-fidelity Figma wireframes, including the four features flagged in
review comments: user set-up (Sign Up), specs ("+" add-spec rows), product
logs (My Listings), and Report.

## How to open

1. Open Android Studio → **Open** → select the `BaryoBenta` folder.
2. Let Gradle sync (Android Studio will download/repair the wrapper if needed).
3. Run the `app` module on an emulator or device (minSdk 24).
4. The app launches on the Splash screen.

## Navigation flow

```
SplashActivity
  -> "Get Started" -> AuthActivity (Login)
       -> toggle -> AuthActivity (Sign Up / user set-up)
       -> submit -> DashboardActivity (Home)

DashboardActivity (Home)
  -> hamburger menu -> ProfileActivity
  -> search bar / category tap -> ListingActivity
  -> location row -> CategoriesActivity
  -> listing card -> ListingDetailsActivity
  -> bottom nav: Home | Browse | Sell | Messages | Profile

ListingActivity (Browse)
  -> listing card -> ListingDetailsActivity

ListingDetailsActivity
  -> Contact Seller -> MessagesActivity
  -> Report (flag icon) -> ReportActivity
  -> Add to Favorites / heart icon -> toggles in place

ProfileActivity (menu hub)
  -> My Listings -> MyListingsActivity (product logs, FAB -> AddListingActivity)
  -> Messages -> MessagesActivity
  -> Categories -> CategoriesActivity
  -> Log Out -> AuthActivity (Login), clears back stack

Bottom nav "Sell" tab (any screen that has it) -> AddListingActivity
  -> "+ Add Spec" dynamically inflates another spec row
  -> Post Listing -> confirms and returns
```

## Files

**Java** — `app/src/main/java/com/baryobenta/community/`
- Activities: `SplashActivity`, `AuthActivity`, `DashboardActivity`,
  `ListingActivity`, `ListingDetailsActivity`, `AddListingActivity`,
  `MyListingsActivity`, `MessagesActivity`, `CategoriesActivity`,
  `ProfileActivity`, `ReportActivity`
- Models: `Listing`, `Category`, `ChatMessage`
- `SampleData` — central sample data provider (no backend/database)
- Adapters: `ListingGridAdapter`, `CategoryCircleAdapter`,
  `CategoryRowAdapter`, `MyListingAdapter`, `MessageAdapter`
- `BottomNavHelper` — wires up the shared 5-tab bottom nav and highlights
  the active tab

**XML layouts** — `app/src/main/res/layout/` (18 files): one `activity_*.xml`
per screen above, plus reusable item layouts (`item_listing_grid`,
`item_category_circle`, `item_category_row`, `item_message`,
`item_my_listing`, `item_spec_row`) and the shared `layout_bottom_nav.xml`.

**Resources** — `app/src/main/res/values/` (`colors.xml`, `strings.xml`,
`themes.xml`, `dimens.xml`) keep color, spacing, and type consistent across
every screen. `app/src/main/res/drawable/` holds all icons and shape
backgrounds as vector/shape drawables (no external image dependencies).

**Manifest** — `app/src/main/AndroidManifest.xml` registers all 11
activities with `SplashActivity` as the launcher.

---

## Feature notes (from Figma review comments)

- **User set-up page** — `AuthActivity` in sign-up mode: full name, email,
  password fields; toggles to/from Login in the same reusable layout.
- **Specs ("+")** — `AddListingActivity` starts with one spec row and lets
  the user tap **+ Add Spec** to inflate more (label + value + remove
  button) via `item_spec_row.xml`, wired up in Java at runtime.
- **Product logs** — `MyListingsActivity` lists the user's posted items
  with a colored status chip (Available / Pending / Sold).
- **Report** — a flag icon on Listing Details opens `ReportActivity` to pick
  a reason and submit (UI demo only); there is also now an explicit
  **"Report this Listing"** button in the action area for visibility.

## Login / session handling

`SessionManager` wraps SharedPreferences to give Login something real to
check, instead of accepting any input:
- **Sign Up** creates a local demo account (name, email, password) and logs
  the user in immediately.
- **Login** first checks whether an account exists at all ("No account
  found — please sign up first"), then validates the email/password against
  it ("Incorrect email or password").
- The session persists across app restarts — `SplashActivity` skips
  straight to the Dashboard if already logged in — and **Log Out** (in the
  Profile menu) clears it.
- This is still local-only, on-device storage, not a real backend — it
  resets if the app's data/storage is cleared.

## KYC (Identity Verification)

`KycActivity`, reached from the Profile menu ("Verify Account (KYC)"), lets
the user "upload" an ID photo (placeholder), enter an ID type and number,
and submit. There's no real identity check — submitting instantly marks the
local demo account as verified via `SessionManager`. Once verified:
- A checkmark badge (`ic_verified`) appears next to the name on the Profile
  header.
- Reopening `KycActivity` shows a "You're verified" confirmation screen
  instead of the form.
- Sample listings from "verified" sellers (iPhone, Mechanical Keyboard)
  show the same badge on their grid cards and Listing Details page, to
  demonstrate how the badge would appear marketplace-wide.

## Ratings & reviews

`Listing` now carries a `rating` (float), `reviewCount` (int), and
`sellerVerified` (boolean). `SampleData` populates realistic values for the
5 sample items. Rating (★ 4.8 · reviews) shows on both the grid cards and
Listing Details. A **"Rate Seller"** link on Listing Details opens
`RateSellerActivity` — a tappable 5-star widget plus an optional comment —
which is a UI demonstration only (no persistence yet).

## Submission description

**Application Name:** Community Buy & Sell Marketplace

**Purpose of the Application:**
Community Buy & Sell Marketplace (BaryoBenta) is a hyperlocal,
community-based marketplace app that lets users browse second-hand items
posted by others nearby, view full listing details, and reach out to
sellers. It's designed to make casual peer-to-peer reselling within a
barangay or campus community simple, organized, and trustworthy.

**Home:** The main feed after login — search, current community location,
a category row, and a grid of nearby sample listings.

**Browse Listings:** The full marketplace grid with category filters,
showing sample second-hand items (iPhone 12 Pro Max, Gaming Laptop,
Mechanical Keyboard, Bluetooth Headphones, Study Desk).

**Listing Details:** Full info for a selected item — image, price,
category, seller, status, description — with Contact Seller, Favorite,
Share, and Report actions.

**Sell an Item:** The posting form with photo upload, category, price,
description, and dynamic specification rows.

**My Listings:** Product logs — the user's own posted items and their
current status.

**Messages / Categories / Profile:** Supporting screens for
conversations, category browsing, and the account menu (with Log Out).
