# Gotify Push Notifications

This document describes how to configure Gotify push notifications in PeerBanHelper.

## Configuration

To set up Gotify push notifications:

1. **Server URL**: The base URL of your Gotify server (e.g., `https://gotify.example.com`)
2. **Application Token**: The token for your Gotify application 
3. **Priority**: Message priority level (1-10, default: 5)

## Example Configuration

### In Web UI
1. Go to Settings → Configuration → Push Notifications
2. Click "Add" to create a new push channel
3. Select "Gotify" as the type
4. Fill in the required fields:
   - **Name**: A unique name for this notification channel
   - **Server URL**: Your Gotify server URL
   - **Application Token**: Your application token from Gotify
   - **Priority**: Message priority (optional, default: 5)

### YAML Configuration
```yaml
push-notification:
  my-gotify:
    type: gotify
    server_url: "https://gotify.example.com"
    token: "your-application-token"
    priority: 5
```

## Getting a Gotify Token

1. Access your Gotify web interface
2. Go to "Apps" section
3. Create a new application or use an existing one
4. Copy the application token
5. Use this token in the PeerBanHelper configuration

## API Details

The implementation sends HTTP POST requests to the Gotify `/message` endpoint with:
- `title`: The notification title
- `message`: The notification content (Markdown is stripped to plain text)
- `priority`: The message priority level

For more information about Gotify, visit: https://gotify.net/docs