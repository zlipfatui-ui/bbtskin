/**
 * BBTSkin Cloudflare Worker API
 * 
 * Endpoints:
 *   GET  /skins        - List all skins
 *   GET  /skins/:uuid  - Get skin for player
 *   PUT  /skins/:uuid  - Save/update skin
 *   DELETE /skins/:uuid - Delete skin
 * 
 * Setup:
 * 1. Create a Cloudflare Worker
 * 2. Create a KV namespace called "BBTSKIN_SKINS"
 * 3. Bind the KV namespace to the worker
 * 4. Set the API_KEY secret in worker settings
 * 5. Deploy this code
 */

// Environment bindings (configured in wrangler.toml or dashboard)
// - BBTSKIN_SKINS: KV Namespace for skin data
// - API_KEY: Secret for authentication

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    
    // CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };
    
    // Handle preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }
    
    // Verify API key
    const authHeader = request.headers.get('Authorization');
    const apiKey = authHeader?.replace('Bearer ', '');
    
    if (apiKey !== env.API_KEY) {
      return jsonResponse({ error: 'Unauthorized' }, 401, corsHeaders);
    }
    
    try {
      // Route handling
      if (path === '/skins' && request.method === 'GET') {
        return await listSkins(env, corsHeaders);
      }
      
      const skinMatch = path.match(/^\/skins\/([a-f0-9-]+)$/i);
      if (skinMatch) {
        const uuid = skinMatch[1];
        
        switch (request.method) {
          case 'GET':
            return await getSkin(env, uuid, corsHeaders);
          case 'PUT':
            return await saveSkin(env, uuid, request, corsHeaders);
          case 'DELETE':
            return await deleteSkin(env, uuid, corsHeaders);
        }
      }
      
      return jsonResponse({ error: 'Not Found' }, 404, corsHeaders);
      
    } catch (error) {
      console.error('Error:', error);
      return jsonResponse({ error: 'Internal Server Error' }, 500, corsHeaders);
    }
  },
};

/**
 * List all skins
 */
async function listSkins(env, corsHeaders) {
  const list = await env.BBTSKIN_SKINS.list();
  const skins = [];
  
  for (const key of list.keys) {
    const data = await env.BBTSKIN_SKINS.get(key.name, 'json');
    if (data) {
      skins.push(data);
    }
  }
  
  return jsonResponse(skins, 200, corsHeaders);
}

/**
 * Get skin for a player
 */
async function getSkin(env, uuid, corsHeaders) {
  const data = await env.BBTSKIN_SKINS.get(uuid, 'json');
  
  if (!data) {
    return jsonResponse({ error: 'Not Found' }, 404, corsHeaders);
  }
  
  return jsonResponse(data, 200, corsHeaders);
}

/**
 * Save/update skin
 */
async function saveSkin(env, uuid, request, corsHeaders) {
  const body = await request.json();
  
  // Validate required fields
  if (!body.imageData) {
    return jsonResponse({ error: 'Missing imageData' }, 400, corsHeaders);
  }
  
  const skinData = {
    uuid: uuid,
    name: body.name || 'Unknown',
    slim: body.slim || false,
    width: body.width || 64,
    height: body.height || 64,
    imageData: body.imageData, // Base64 encoded
    timestamp: body.timestamp || Date.now(),
    updatedAt: Date.now(),
  };
  
  // Store in KV (with 30 day expiration if you want auto-cleanup)
  await env.BBTSKIN_SKINS.put(uuid, JSON.stringify(skinData), {
    // expirationTtl: 60 * 60 * 24 * 30, // 30 days (optional)
  });
  
  return jsonResponse({ success: true, uuid: uuid }, 200, corsHeaders);
}

/**
 * Delete skin
 */
async function deleteSkin(env, uuid, corsHeaders) {
  await env.BBTSKIN_SKINS.delete(uuid);
  return jsonResponse({ success: true }, 200, corsHeaders);
}

/**
 * JSON response helper
 */
function jsonResponse(data, status, headers = {}) {
  return new Response(JSON.stringify(data), {
    status: status,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
  });
}
