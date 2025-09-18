# YouTube Cookie Configuration Guide

## Problem Description

When you deploy the project but cannot download YouTube episodes normally, and see the following error message in the service logs:

```
Sign in to confirm you're not a bot.
```

This indicates that YouTube has detected requests from abnormal IP addresses and activated risk control measures. This situation typically occurs when:

- Client IP originates from data centers
- Deployed on cloud servers
- High network request frequency
- Other network environments that YouTube considers potentially risky

## Solution

You can resolve this issue by configuring YouTube cookies in "User Settings".

## Important Warnings

⚠️ **Usage Risk Warning**: Using this project with your YouTube account carries the risk of account suspension (temporary or permanent). Please be mindful of request rates and download volumes. Use only when necessary, or consider using a throwaway account.

📝 **Use Cases**: This feature is only necessary for accessing content that requires account permissions, such as private playlists, age-restricted videos, and members-only content.

## Cookie Export Steps

### Step 1: Prepare Browser Extension

First, install the appropriate browser extension to export cookies:

- **Chrome Users**: Install [Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) extension
- **Firefox Users**: Install [cookies.txt](https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/) extension

⚠️ **Security Warning**: Be careful about browser extensions you install. If you previously installed the "Get cookies.txt" (not "LOCALLY" version) Chrome extension, it's recommended to uninstall it immediately; it has been reported as malware and removed from the Chrome Web Store.

### Step 2: Export Cookies Using Private Browsing Mode

To ensure the exported cookies won't be frequently rotated by YouTube, follow these steps:

1. **Open Private Browsing Window**
   - Open a new private browsing/incognito window
   - Log into your YouTube account

2. **Navigate to Specific Page**
   - In the same private browsing tab from step 1, navigate to: `https://www.youtube.com/robots.txt`
   - Ensure this is the only private browsing tab open

3. **Export Cookies**
   - Use the installed browser extension to export `youtube.com` cookies
   - Save the cookies as a `cookies.txt` file

4. **Close Browser Window**
   - After exporting, immediately close the private browsing window
   - Ensure the session is never opened in the browser again

### Step 3: Upload Cookie File

1. Log into your PigeonPod
2. Go to "User Settings" page
3. Find the "Set Cookies" button
4. Upload the `cookies.txt` file you just exported
5. Save settings
6. Retry downloading

## Important Notes

- This project only supports uploading `cookies.txt` files exported through browser extensions
- Does not support using `yt-dlp --cookies-from-browser` or similar command-line methods
- Keep cookie files private and do not share them with others
- If downloads still fail, you may need to re-export updated cookies
- It's recommended to update cookies periodically to ensure continued availability

## Troubleshooting

If you still cannot download after configuring cookies:

1. Confirm the cookie file format is correct
2. Check if your YouTube account is functioning normally
3. Try re-exporting cookies
4. Consider changing network environment or IP address

---

By properly configuring YouTube cookies, you should be able to resolve most download issues caused by IP risk control measures. If you have other questions, please refer to the project documentation or contact technical support.
