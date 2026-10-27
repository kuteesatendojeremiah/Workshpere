/**
 * Where the WebSocket lives. In development the API has its own address (http://localhost:8080), so the
 * socket is the same address with ws:// instead of http://. In the Docker setup the browser talks to
 * ONE address (nginx serves the pages and proxies /api and /ws), so the API base is empty and the socket
 * must be built from the page's own address, using wss:// when the page itself is served over https.
 */
export function brokerUrl(apiBase: string, page: { protocol: string; host: string }): string {
  if (apiBase === '') {
    return `${page.protocol === 'https:' ? 'wss' : 'ws'}://${page.host}/ws`
  }
  return `${apiBase.replace(/^http/, 'ws')}/ws`
}
