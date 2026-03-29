# Set Environment Variables
$env:JWT_SECRET="super_secret_very_long_key_for_development_purposes_only_1234567890"
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="amenbank"
$env:DB_USER="root"
$env:DB_PASSWORD=""
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD=""
$env:ADMIN_SECRET_KEY_HASH='$2a$12$ZpWlX1H2U3R4I5C6H7S8E9K1E2Y3H4A5S6H7O8F9A1D2M3I4N5'
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="noreply@amenbank.com"
$env:MAIL_PASSWORD="change_me"
$env:MAIL_FROM="noreply@amenbank.com"
$env:MAIL_FROM_NAME="Amen Bank"
$env:UPLOAD_PATH="./uploads"
$env:APP_PORT="8080"

# Start the Backend
Write-Host "🚀 Starting Amen Bank Backend..."
mvn spring-boot:run
