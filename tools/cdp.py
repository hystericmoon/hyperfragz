"""Minimal Chrome DevTools client for the app's hidden WebView (debug builds).

Run tools/fwd.sh first. Uses only the standard library.

  cdp.py page.js            evaluate JS (awaits promises), print the JSON result
  cdp.py --profile [secs]   sample the page's JS for N seconds (default 5); top self-time + callers
  cdp.py --reload pre.js page.js [secs]
                            inject pre.js at document start, reload, wait, then evaluate page.js
                            (e.g. pre.js records timings into window.__log, page.js returns it)

MAXOUT env var caps printed characters (default 20000).
"""
import base64, collections, json, os, socket, struct, sys, time, urllib.request

pages = [p for p in json.load(urllib.request.urlopen('http://localhost:9222/json', timeout=10))
         if p.get('type') == 'page' and 'fragrantica.com' in p.get('url', '')]
url = pages[0]['webSocketDebuggerUrl']; host, path = url[5:].split('/', 1)
h, p = host.split(':'); s = socket.create_connection((h, int(p)), timeout=15)
key = base64.b64encode(os.urandom(16)).decode()
s.send(f"GET /{path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
       f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
buf = b''
while b'\r\n\r\n' not in buf: buf += s.recv(4096)

def send(o):
    d = json.dumps(o).encode(); m = os.urandom(4); hdr = bytes([0x81]); n = len(d)
    hdr += bytes([0x80 | n]) if n < 126 else (bytes([0x80 | 126]) + struct.pack('>H', n) if n < 65536 else bytes([0x80 | 127]) + struct.pack('>Q', n))
    s.send(hdr + m + bytes(b ^ m[i % 4] for i, b in enumerate(d)))

def recvexact(n):
    b = b''
    while len(b) < n: b += s.recv(n - len(b))
    return b

def recv():
    hh = recvexact(2); n = hh[1] & 127
    if n == 126: n = struct.unpack('>H', recvexact(2))[0]
    elif n == 127: n = struct.unpack('>Q', recvexact(8))[0]
    return recvexact(n)

def call(i, method, params=None):
    send({'id': i, 'method': method, 'params': params or {}})
    while True:
        r = json.loads(recv())
        if r.get('id') == i: return r.get('result', {})

def evaluate(js):
    res = call(1, 'Runtime.evaluate', {'expression': js, 'awaitPromise': True, 'returnByValue': True})
    print(json.dumps(res.get('result', {}).get('value', res), indent=1)[:int(os.environ.get('MAXOUT', '20000'))])

def drain(secs):
    end = time.time() + secs
    while time.time() < end:
        s.settimeout(max(0.1, end - time.time()))
        try: recv()
        except Exception: break
    s.settimeout(15)

args = sys.argv[1:]
if args and args[0] == '--profile':
    secs = float(args[1]) if len(args) > 1 else 5
    call(2, 'Profiler.enable'); call(3, 'Profiler.setSamplingInterval', {'interval': 500}); call(4, 'Profiler.start')
    time.sleep(secs)
    prof = call(5, 'Profiler.stop')['profile']
    nodes = {n['id']: n for n in prof['nodes']}
    parent = {c: n['id'] for n in prof['nodes'] for c in n.get('children', [])}
    cnt = collections.Counter(prof['samples']); tot = sum(cnt.values())
    def name(nid):
        f = nodes[nid]['callFrame']
        return f"{f['functionName'] or '(anon)'} {f['url'].split('/')[-1][:45]}:{f['lineNumber']}"
    agg = collections.Counter()
    for nid, c in cnt.items(): agg[name(nid)] += c
    print('samples', tot)
    for k, v in agg.most_common(20): print(f"{100*v/tot:5.1f}% {k}")
    print('-- callers of the hottest native calls')
    pc = collections.Counter()
    for nid, c in cnt.items():
        if nodes[nid]['callFrame']['url'] == '' and nodes[nid]['callFrame']['functionName'] not in ('(idle)', '(root)'):
            chain, q = [], parent.get(nid)
            while q and len(chain) < 4: chain.append(name(q)); q = parent.get(q)
            pc[name(nid) + ' <- ' + ' <- '.join(chain)] += c
    for k, v in pc.most_common(8): print(f"{100*v/tot:5.1f}% {k}")
elif args and args[0] == '--reload':
    call(10, 'Page.enable')
    call(11, 'Page.addScriptToEvaluateOnNewDocument', {'source': open(args[1]).read()})
    send({'id': 12, 'method': 'Page.reload', 'params': {}})
    drain(float(args[3]) if len(args) > 3 else 12)
    evaluate(open(args[2]).read())
else:
    evaluate('\n'.join(open(f).read() for f in args))
