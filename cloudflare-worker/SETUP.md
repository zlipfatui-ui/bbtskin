# BBTSkin Cloudflare Worker Setup Guide

This guide will help you set up the Cloudflare Worker backend for BBTSkin to persist skins across server restarts and allow late-joining players to see existing skins.

## Prerequisites

- A Cloudflare account (free tier works fine)
- Your domain (beforebedtime.net) added to Cloudflare
- Node.js installed (for wrangler CLI)

## Step 1: Install Wrangler CLI

```bash
npm install -g wrangler
```

## Step 2: Login to Cloudflare

```bash
wrangler login
```

This will open a browser window to authenticate.

## Step 3: Create the KV Namespace

KV (Key-Value) storage is where skin data will be stored.

```bash
cd cloudflare-worker
wrangler kv:namespace create "BBTSKIN_SKINS"
```

You'll get output like:
```
⛅️ wrangler 3.x.x
🌀 Creating namespace with title "bbtskin-api-BBTSKIN_SKINS"
✨ Success!
Add the following to your configuration file in your kv_namespaces array:
{ binding = "BBTSKIN_SKINS", id = "abc123def456..." }
```

**Copy the `id` value** and paste it into `wrangler.toml`:
```toml
kv_namespaces = [
  { binding = "BBTSKIN_SKINS", id = "abc123def456..." }
]
```

## Step 4: Set the API Key Secret

Generate a strong random API key (e.g., use a password generator, 32+ characters).

```bash
wrangler secret put API_KEY
```

Enter your API key when prompted. **Save this key** - you'll need it for the mod config.

## Step 5: Deploy the Worker

```bash
wrangler deploy
```

You'll get a URL like: `https://bbtskin-api.YOUR_SUBDOMAIN.workers.dev`

## Step 6: Set Up Custom Domain (api.beforebedtime.net)

### Option A: Via Cloudflare Dashboard

1. Go to Cloudflare Dashboard → Workers & Pages
2. Click on your `bbtskin-api` worker
3. Go to "Settings" → "Triggers"
4. Click "Add Custom Domain"
5. Enter: `api.beforebedtime.net`
6. Cloudflare will automatically create the DNS record

### Option B: Via wrangler.toml

Uncomment and update the routes in `wrangler.toml`:
```toml
routes = [
  { pattern = "api.beforebedtime.net/*", zone_name = "beforebedtime.net" }
]
```

Then redeploy:
```bash
wrangler deploy
```

## Step 7: Configure the Minecraft Mod

Edit `config/bbtskin.json` on your Minecraft server:

```json
{
  "cloudflareWorkerUrl": "https://api.beforebedtime.net",
  "cloudflareApiKey": "YOUR_API_KEY_HERE",
  "enableCloudflareSync": true,
  ...
}
```

## Step 8: Test the API

```bash
# Test listing skins (should return empty array initially)
curl -H "Authorization: Bearer YOUR_API_KEY" https://api.beforebedtime.net/skins

# Expected response: []
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /skins | List all stored skins |
| GET | /skins/{uuid} | Get skin for a specific player |
| PUT | /skins/{uuid} | Save/update a player's skin |
| DELETE | /skins/{uuid} | Remove a player's skin |

## Troubleshooting

### "Unauthorized" error
- Check that the API key in mod config matches the one set with `wrangler secret put`

### Skins not persisting
- Check server logs for API errors
- Verify the worker is deployed: `wrangler tail` to see live logs

### DNS not working
- Wait a few minutes for DNS propagation
- Check Cloudflare dashboard for DNS record
- Try the workers.dev URL directly first

## Cost Considerations

Cloudflare Workers Free Tier includes:
- 100,000 requests/day
- 10ms CPU time per request
- KV: 100,000 reads/day, 1,000 writes/day

This is more than enough for a Minecraft server. Even with 100 players constantly changing skins, you'd be well under limits.

## Security Notes

1. **Keep your API key secret** - don't commit it to git
2. The API key should be strong (32+ random characters)
3. Only the Minecraft server should have the API key
4. The worker validates the key on every request

## Upgrading Storage (Optional)

If you need more storage or features, consider:

- **R2**: For larger skin files (images stored as objects)
- **D1**: For SQL-based queries (e.g., search by name)
- **Durable Objects**: For real-time sync features

For most servers, KV is sufficient.
