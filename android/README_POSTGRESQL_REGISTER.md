
---

# Group Activity — Connect Register to PostgreSQL

This section documents the new work: connecting the Sign Up (Register)
screen to a real PostgreSQL database through a PHP REST API, following the
same safe pattern taught in the earlier lab —
**Android → Retrofit → PHP → PDO → PostgreSQL** — and never Android
connecting to PostgreSQL directly.

## What's new

- `backend/schema.sql` — creates `baryobenta_db` and a `users` table with
  `UNIQUE` constraints on `username` and `email`.
- `backend/db.php` — the single shared PDO connection (credentials live
  here only, server-side; Android never sees them).
- `backend/register.php` — validates the input, checks for an existing
  username/email, hashes the password with bcrypt, and inserts the row
  with a prepared statement.
- `backend/test_connection.php` — quick isolated check that PHP can reach
  PostgreSQL, same pattern as the earlier lab.
- Android: `User.java`, `RegisterResponse.java`, `ApiService.java`,
  `RetrofitClient.java` (new), plus `AuthActivity.java` and
  `activity_auth.xml` updated — Sign Up now has a Username field and a
  status message, and submitting it calls the real API instead of only
  saving locally.

## Setup steps (do this before running the Android app)

1. **PostgreSQL**: open pgAdmin 4's Query Tool and run `backend/schema.sql`
   (the `CREATE DATABASE` line first, then reconnect the Query Tool to
   `baryobenta_db` for the `CREATE TABLE` line).
2. **PHP**: copy `db.php`, `register.php`, and `test_connection.php` into
   `C:\xampp\htdocs\baryobenta_api\` (create that folder). Open `db.php`
   and replace `YOUR_POSTGRES_PASSWORD` with your actual postgres password
   — **do not commit the real password**.
3. Start Apache and PostgreSQL, then verify in a browser, in order:
   - `http://localhost/baryobenta_api/test_connection.php` → should print
     "PostgreSQL connection successful!"
   - POST to `http://localhost/baryobenta_api/register.php` with a tool
     like Postman (JSON body: `fullName`, `username`, `email`, `password`)
     → should return `{"success":true,"message":"Account created successfully!"}`
     and the row should appear in pgAdmin 4 under `users`.
4. **Only once the API works on its own**, open the Android project. If
   you're on the standard emulator, `RetrofitClient`'s `BASE_URL` of
   `http://10.0.2.2/baryobenta_api/` already points at your XAMPP install.
   On a physical device, change it to your PC's `192.168.x.x` address
   (same Wi-Fi as the phone).
5. Run the app → Splash → Get Started → tap "Don't have an account? Sign
   up" → fill in the form → Save.

## How the three required demo scenarios map to the code

- **Successful registration**: valid, unused username/email/password →
  `register.php` inserts the row and returns `success:true`; the app shows
  the green success message, then moves to the Dashboard.
- **Empty required field**: caught client-side in `AuthActivity.handleSignUp()`
  before any network call — the specific empty field shows an inline error
  and nothing is sent.
- **Existing username or email**: `register.php` runs a `SELECT` for that
  username/email before inserting; if found, it returns `success:false`
  with a specific message ("That username is already taken." or "That
  email is already registered."), shown in red on-screen, and no row is
  inserted. The `UNIQUE` constraints in `schema.sql` are a second line of
  defense against the same problem at the database level.

## Explaining the data flow (for the group's write-up)

A ready-to-adapt paragraph for the "brief explanation" deliverable:

> When the user taps Save on the Register screen, Android builds a `User`
> object (full name, username, email, password) and Retrofit serializes it
> to JSON and sends it as an HTTP POST to `register.php` on the PHP server
> — Android never talks to PostgreSQL directly. `register.php` validates
> the fields, uses PDO to query PostgreSQL for a matching username or
> email, and if none exists, hashes the password and inserts a new row
> into the `users` table with a prepared statement. PostgreSQL stores the
> row and PHP encodes a JSON response (`success` and `message`) back to
> Android. Retrofit's `GsonConverterFactory` converts that JSON into a
> `RegisterResponse` object, and `AuthActivity` reads it to show the
> success or error message on screen.

## What this project can't do for you

I can't run Android Studio, XAMPP, or pgAdmin 4 myself, so I can't
generate the actual screenshots, the screen recording, or verify the live
demo — those need to be captured by the group running the real app against
a real PostgreSQL instance. Everything above is written and verified to be
internally consistent (matching resource IDs, valid XML, matching field
names between the Java models and the PHP endpoint), but only running it
end-to-end confirms it works on your machine.

## Security notes

- `db.php`'s credentials never leave the server — Android only ever calls
  `register.php` over HTTP, and gets back a JSON message, never the
  database username/password.
- Passwords are hashed with `password_hash()` (bcrypt) before being
  stored — the database never holds a plain-text password.
- `register.php` logs real error details with `error_log()` on the server
  only; it never echoes exception messages back to the client.
- `android:usesCleartextTraffic="true"` and the plain `http://` base URL
  are for local lab testing only (same as the earlier lab's warning) —
  a production app should use HTTPS.
