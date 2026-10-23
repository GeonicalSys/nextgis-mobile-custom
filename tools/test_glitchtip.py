"""Technical API-policy tests; no production reports or credentials."""
import unittest
from unittest.mock import Mock
from glitchtip import Client, GlitchTipError, next_page, read_credentials


class ConnectionTest(unittest.TestCase):
    def test_existing_operator_file_and_json_are_supported_without_reformatting(self):
        self.assertEqual(('operator@example.test', 'with:colon'), read_credentials(
            'Адрес: https://invalid.example/errors/\nЛогин: operator@example.test\nПароль: with:colon'))
        self.assertEqual(('x', 'y'), read_credentials('{"email":"x","password":"y"}'))
        with self.assertRaises(ValueError):
            read_credentials('URL: https://invalid.example')

    def test_reverse_proxy_pagination_keeps_the_same_service_and_query(self):
        for path in ('/api/0/issues/45/events/', '/errors/api/0/issues/45/events/'):
            self.assertEqual('/api/0/issues/45/events/?cursor=a%3D&limit=100', next_page(
                f'<https://test.invalid{path}?cursor=a%3D&limit=100>; rel="next"; results="true"',
                'https://test.invalid', '/errors'))
        self.assertIsNone(next_page('<https://test.invalid/>; rel="next"; results="false"',
                                    'https://test.invalid', '/errors'))

    def test_external_pagination_and_unexpected_routes_are_rejected(self):
        for url in ('https://other.invalid/api/0/issues/', 'http://test.invalid/api/0/issues/',
                    'https://test.invalid/admin/', 'https://user:secret@test.invalid/api/0/issues/'):
            with self.assertRaises(GlitchTipError):
                next_page(f'<{url}>; rel="next"; results="true"', 'https://test.invalid', '/errors')

    def client(self):
        return Client({'base_url':'https://test.invalid/errors','organization':'test',
                       'project':'mobile','credentials_file':'unused'})

    def test_all_pages_are_read_and_a_loop_is_rejected(self):
        c = self.client()
        def page(route):
            c.next_link = ('<https://test.invalid/api/0/issues/?cursor=two>; rel="next"; results="true"'
                           if route.endswith('one') else '')
            return [route]
        c.get = page
        self.assertEqual(2, len(c.all('/api/0/issues/?cursor=one')))
        def loop(route):
            c.next_link = '<https://test.invalid/api/0/issues/>; rel="next"; results="true"'
            return []
        c.get = loop
        with self.assertRaises(GlitchTipError): c.all('/api/0/issues/')

    def test_wrong_project_and_new_events_cannot_be_resolved(self):
        c = self.client(); c.get = Mock(return_value={'project':{'slug':'desktop'}})
        c.request = Mock()
        with self.assertRaises(GlitchTipError): c.resolve(1, 'reviewed')
        c.get.return_value = {'project':{'slug':'mobile'},'status':'unresolved','count':'3','lastSeen':'new'}
        with self.assertRaises(GlitchTipError): c.resolve(1, 'reviewed', {'count':'2','lastSeen':'old'})
        c.request.assert_not_called()

    def test_resolution_keeps_events_and_posts_disposition_before_status(self):
        c = self.client(); c.get = Mock(return_value={'project':{'slug':'mobile'},'status':'unresolved'})
        c.request = Mock(side_effect=[({'id':'4'},201),({'status':'resolved','count':'2'},200)])
        self.assertEqual('resolved', c.resolve(1, 'Fix in draft; not released')['status'])
        self.assertEqual('POST', c.request.call_args_list[0].args[2])
        self.assertEqual({'status':'resolved'}, c.request.call_args_list[1].args[1])
        self.assertEqual('PUT', c.request.call_args_list[1].args[2])


if __name__ == '__main__': unittest.main()
