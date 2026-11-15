#!/usr/bin/env python3
"""Cross-checks frontend routes/links/API calls against backend controllers (method + path + roles) and nginx.
Run from the repo root: python scripts/verify-integration.py   Exit code 1 on any mismatch."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
fe, be = root / 'frontend/src', root / 'opsmind-core/src/main/java'
norm = lambda p: re.sub(r'(\$\{[^}]*\}|\{[^}]*\})', '{}', p).rstrip('/') or '/'
problems = []

# ---- routes vs links
app = (fe / 'App.jsx').read_text()
routes = {norm(m) for m in re.findall(r'<Route\s+path="([^"]+)"', app) if m != '*'}
routes = {re.sub(r':\w+', '{}', r) for r in routes}
links = set()
for f in fe.rglob('*.jsx'):
    if f.name.endswith('.test.jsx'): continue
    for m in re.findall(r'\bto=(?:"([^"]+)"|\{`([^`]+)`\})', f.read_text()):
        t = m[0] or m[1]
        if t.startswith('/'): links.add(norm(t.split('#')[0]) )
    for m in re.findall(r'href="(/[^"]*)"', f.read_text()): links.add(norm(m))
print('ROUTES:', ', '.join(sorted(routes)))
for l in sorted(links):
    ok = l in routes
    print(f"  link {l:28} {'OK' if ok else 'NO ROUTE'}")
    if not ok: problems.append(f'link {l} has no route')

# ---- backend endpoints
endpoints = []  # (method, path, roles)
for f in be.rglob('*Controller.java'):
    s = f.read_text()
    cls = re.search(r'(@PreAuthorize\("([^"]*)"\)\s*)?(?:@\w+(?:\([^)]*\))?\s*)*public class', s, re.S)
    cls_roles = None
    head = s[:s.index('public class')]
    m = re.findall(r'@PreAuthorize\("([^"]*)"\)', head); cls_roles = m[-1] if m else None
    pm = re.findall(r'@RequestMapping\("([^"]*)"\)', head); prefix = pm[-1] if pm else ''
    body = s[s.index('public class'):]
    for mm in re.finditer(r'@(Get|Post|Patch|Put|Delete)Mapping(?:\("([^"]*)"\))?', body):
        sig_end = body.index('{', mm.end())
        window = body[max(0, mm.start() - 160): sig_end]
        pa = re.findall(r'@PreAuthorize\("([^"]*)"\)', window)
        roles = pa[-1] if pa else cls_roles
        r = re.findall(r"'(\w+)'", roles) if roles else ['(any authenticated)']
        endpoints.append((mm.group(1).upper(), norm(prefix + (mm.group(2) or '')), r))
public = {'/api/auth/login', '/api/auth/register'}

# ---- frontend calls
calls = set()
for f in (fe / 'api').glob('*.js'):
    s = f.read_text()
    for m in re.finditer(r'(?:apiFetch|safeFetch)\(\s*([`\'])(/api/.*?)\1(?:\s*,\s*\{(.*?)\})?\s*\)', s, re.S):
        method = re.search(r"method:\s*'(\w+)'", m.group(3) or '')
        calls.add(((method.group(1) if method else 'GET'), norm(m.group(2))))
print('\nAPI CALLS (frontend -> backend):')
for method, path in sorted(calls, key=lambda x: (x[1], x[0])):
    hit = [e for e in endpoints if e[0] == method and e[1] == path]
    if hit: print(f"  {method:6} {path:62} OK  roles={','.join(hit[0][2])}")
    else:
        print(f"  {method:6} {path:62} NO BACKEND MAPPING"); problems.append(f'{method} {path} unmapped')

# ---- role gating consistent between nav and backend
layout = (fe / 'components/Layout.jsx').read_text()
print('\nROLE GATING:')
chk = [('Experiments nav (ADMIN/SRE/ENGINEER)', "['ADMIN', 'SRE', 'ENGINEER']" in layout, 'GET', '/api/fault-lab/experiments', {'ADMIN', 'SRE', 'ENGINEER'}),
       ('Events nav (ADMIN/SRE)', "['ADMIN', 'SRE'].includes(session?.role)" in layout, 'GET', '/api/audit-events', {'ADMIN', 'SRE'}),
       ('Emergency stop (ADMIN/SRE only)', True, 'POST', '/api/fault-lab/emergency-stop', {'ADMIN', 'SRE'})]
for name, fe_ok, method, path, want in chk:
    hit = [e for e in endpoints if e[0] == method and e[1] == path]
    got = set(hit[0][2]) if hit else None
    good = fe_ok and got == want
    print(f"  {name:42} backend={sorted(got) if got else None}  {'OK' if good else 'MISMATCH'}")
    if not good: problems.append(f'role gating {name}')

# ---- nginx
ng = (root / 'frontend/nginx.conf').read_text()
print('\nNGINX:')
for p in ['/api/', '/api/auth/', '/health-proxy/order/', '/health-proxy/payment/', '/health-proxy/inventory/', '/health-proxy/catalog/']:
    ok = f'location {p} ' in ng or f'location {p}{{' in ng
    print(f"  location {p:28} {'OK' if ok else 'MISSING'}")
    if not ok: problems.append(f'nginx {p}')
for f in (fe / 'api/health.js',):
    for p in re.findall(r"path: '(/health-proxy/[^']+)'", f.read_text()):
        ok = f'location {p} ' in ng; print(f"  health.js uses {p:24} {'OK' if ok else 'NOT PROXIED'}")
        if not ok: problems.append(f'health {p}')

print('\nRESULT:', 'ALL CHECKS PASSED' if not problems else 'PROBLEMS: ' + '; '.join(problems))
sys.exit(1 if problems else 0)
