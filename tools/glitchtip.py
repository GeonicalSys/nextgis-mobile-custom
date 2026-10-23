"""GlitchTip triage CLI. Connection settings are versioned; credentials never are."""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


class GlitchTipError(RuntimeError):
    pass


class Client:
    def __init__(self, config, credentials=None, opener=None):
        base = urllib.parse.urlsplit(config['base_url'].rstrip('/'))
        if base.scheme != 'https' or base.username or base.password or base.query or base.fragment:
            raise GlitchTipError('base_url must be an HTTPS URL without credentials, query or fragment')
        self.origin = f'{base.scheme}://{base.netloc}'
        self.prefix = base.path.rstrip('/')
        self.org, self.project = config['organization'], config['project']
        self.credentials = Path(os.path.expandvars(credentials or os.environ.get(
            'GLITCHTIP_CREDENTIALS_FILE', config['credentials_file']))).expanduser()
        self.cookies = http.cookiejar.CookieJar()  # In memory only; no token/session export.
        self.opener = opener or urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.next_link = ''

    def request(self, route, body=None, method=None, accepted=(200,)):
        if not route.startswith('/') or route.startswith('//'):
            raise GlitchTipError('Expected an API path')
        headers = {'Accept': 'application/json', 'Referer': self.origin + self.prefix + '/',
                   'Origin': self.origin}
        csrf = next((c.value for c in self.cookies if c.name == 'csrftoken'), None)
        if csrf:
            headers['X-CSRFToken'] = csrf
        if body is not None:
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.origin + self.prefix + route,
            data=None if body is None else json.dumps(body).encode('utf-8'),
            headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=45)
        except urllib.error.HTTPError as error:
            response = error
        except (OSError, urllib.error.URLError):
            raise GlitchTipError('Cannot connect to GlitchTip; check network and service availability') from None
        with response:
            if response.status not in accepted:
                # Server bodies can contain private data. Do not print them or credentials.
                raise GlitchTipError(f'GlitchTip returned HTTP {response.status}')
            self.next_link = response.headers.get('Link', '')
            return json.load(response), response.status

    def login(self):
        session, status = self.request('/_allauth/browser/v1/auth/session', accepted=(200, 401))
        if status == 200 and session.get('meta', {}).get('is_authenticated'):
            return
        try:
            text = self.credentials.read_text(encoding='utf-8-sig')
            email, password = read_credentials(text)
        except (OSError, ValueError, KeyError):
            raise GlitchTipError('Cannot read credentials; see docs/runbooks/glitchtip-triage.md') from None
        result, status = self.request('/_allauth/browser/v1/auth/login',
            {'email': email, 'password': password}, 'POST', accepted=(200, 400, 401, 403))
        if status != 200 or not result.get('meta', {}).get('is_authenticated'):
            raise GlitchTipError('GlitchTip login failed; check the private credential file')

    def get(self, route):
        return self.request(route)[0]

    def all(self, route):
        rows, seen = [], set()
        while route:
            if route in seen:
                raise GlitchTipError('Repeated pagination cursor')
            seen.add(route)
            rows.extend(self.get(route))
            route = next_page(self.next_link, self.origin, self.prefix)
        return rows

    def issues(self, query='is:unresolved'):
        return self.all(f'/api/0/projects/{self.org}/{self.project}/issues/?' +
                        urllib.parse.urlencode({'query': query, 'limit': 100}))

    def checked_issue(self, issue_id):
        issue = self.get(f'/api/0/issues/{issue_id}/')
        if issue.get('project', {}).get('slug') != self.project:
            raise GlitchTipError(f'Issue {issue_id} belongs to another project')
        return issue

    def resolve(self, issue_id, comment, expected=None):
        issue = self.checked_issue(issue_id)
        if issue['status'] == 'resolved':
            return {'id': str(issue_id), 'status': 'already resolved'}
        if expected and any(str(issue.get(k)) != str(expected.get(k)) for k in ('count', 'lastSeen')):
            raise GlitchTipError(f'Issue {issue_id} changed since export; inspect its new events first')
        self.request(f'/api/0/issues/{issue_id}/comments/', {'data': {'text': comment}}, 'POST', accepted=(201,))
        result, _ = self.request(f'/api/0/issues/{issue_id}/', {'status': 'resolved'}, 'PUT')
        if result.get('status') != 'resolved':
            raise GlitchTipError(f'Issue {issue_id} was not resolved')
        return {'id': str(issue_id), 'status': result['status'], 'count': result.get('count')}


def read_credentials(text):
    if text.lstrip().startswith('{'):
        data = json.loads(text)
        return data['email'], data['password']
    fields = {}
    for line in text.splitlines():
        if ':' in line:
            key, value = line.split(':', 1)
            fields[key.strip().lower()] = value.strip()
    email = fields.get('логин') or fields.get('email') or fields.get('login')
    password = fields.get('пароль') or fields.get('password')
    if not email or not password:
        raise ValueError('Missing credentials')
    return email, password


def next_page(link, origin, prefix):
    for part in link.split(','):
        if 'rel="next"' not in part or 'results="true"' not in part:
            continue
        match = re.search(r'<([^>]+)>', part)
        if not match:
            raise GlitchTipError('Malformed pagination link')
        parsed = urllib.parse.urlsplit(urllib.parse.urljoin(origin + prefix + '/', match[1]))
        if f'{parsed.scheme}://{parsed.netloc}' != origin or parsed.username or parsed.password:
            raise GlitchTipError('Pagination points outside GlitchTip')
        # GlitchTip 6.2.6 behind /errors emits links without its reverse-proxy prefix.
        route = parsed.path.removeprefix(prefix) if prefix else parsed.path
        if not route.startswith('/api/0/'):
            raise GlitchTipError('Unexpected pagination route')
        return route + ('?' + parsed.query if parsed.query else '')
    return None


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=Path(__file__).with_suffix('.json'))
    parser.add_argument('--credentials-file')
    commands = parser.add_subparsers(dest='command', required=True)
    listing = commands.add_parser('list', help='List mobile groups; read-only')
    listing.add_argument('--all', action='store_true')
    show = commands.add_parser('show', help='Read one group and its events/comments')
    show.add_argument('id', type=int)
    export = commands.add_parser('export', help='Save raw private evidence outside Git')
    export.add_argument('--output', required=True, type=Path)
    resolve = commands.add_parser('resolve', help='Comment and resolve one reviewed group')
    resolve.add_argument('id', type=int)
    resolve.add_argument('--comment-file', required=True, type=Path)
    resolve.add_argument('--snapshot', type=Path, help='Exported issues.json; refuse if new events arrived')
    args = parser.parse_args(argv)
    try:
        client = Client(json.loads(args.config.read_text(encoding='utf-8')), args.credentials_file)
        client.login()
        if args.command == 'list':
            for issue in client.issues('' if args.all else 'is:unresolved'):
                print(json.dumps({k: issue.get(k) for k in ('id','shortId','title','count','status','lastSeen')}, ensure_ascii=False))
        elif args.command == 'show':
            issue = client.checked_issue(args.id)
            print(json.dumps({'issue': issue, 'events': client.all(f'/api/0/issues/{args.id}/events/?limit=100'),
                              'comments': client.all(f'/api/0/issues/{args.id}/comments/?limit=100')}, ensure_ascii=False, indent=2))
        elif args.command == 'export':
            # Raw reports can include event-supplied credentials. Refuse any Git checkout.
            target = args.output.resolve()
            if any((p / '.git').exists() for p in (target, *target.parents)):
                raise GlitchTipError('Raw evidence must be stored outside any Git checkout, preferably on Q')
            target.mkdir(parents=True, exist_ok=True)
            issues = client.issues()
            save(target / 'issues.json', issues)
            for issue in issues:
                ident = int(issue['id'])
                save(target / f'{ident}-events.json', client.all(f'/api/0/issues/{ident}/events/?limit=100'))
                save(target / f'{ident}-comments.json', client.all(f'/api/0/issues/{ident}/comments/?limit=100'))
            print(f'Exported {len(issues)} groups to {target}')
        else:
            expected = None
            if args.snapshot:
                expected = next((i for i in json.loads(args.snapshot.read_text(encoding='utf-8'))
                                 if str(i['id']) == str(args.id)), None)
                if expected is None:
                    raise GlitchTipError('Issue is absent from the provided snapshot')
            comment = args.comment_file.read_text(encoding='utf-8').strip()
            if not comment:
                raise GlitchTipError('A nonempty disposition comment is required')
            print(json.dumps(client.resolve(args.id, comment, expected), ensure_ascii=False))
    except (GlitchTipError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    sys.exit(main())
