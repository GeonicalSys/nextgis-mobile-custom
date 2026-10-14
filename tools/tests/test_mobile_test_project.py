import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_test_project import choose_project, ProjectSelectionRequired, read_projects, snapshot_project
import json


class PermittedProjectTest(unittest.TestCase):
    def setUp(self):
        self.projects=[{'directory':'collector_1403_test','id':1403,'name':'Для инженеров Геоникл'},
                       {'directory':'other','id':42,'name':'Рабочий проект'}]
    def test_the_explicitly_authorized_project_is_selected(self):
        self.assertEqual(1403,choose_project(self.projects,'1403')['id'])
    def test_missing_project_asks_instead_of_using_the_first_available(self):
        with self.assertRaisesRegex(ProjectSelectionRequired,'На каком проекте'):
            choose_project(self.projects[1:],'1403')
    def test_same_remote_id_on_two_accounts_requires_an_explicit_choice(self):
        self.projects.append({'directory':'duplicate','id':1403,'name':'Другой'})
        with self.assertRaises(ProjectSelectionRequired):choose_project(self.projects,'1403')
        self.assertEqual('collector_1403_test',choose_project(self.projects,'collector_1403_test')['directory'])
    def test_an_explicitly_selected_replacement_is_accepted(self):
        self.assertEqual(42,choose_project(self.projects,'Рабочий проект')['id'])
    def test_unrelated_incomplete_workspace_does_not_hide_the_permitted_project(self):
        def shell(*args):
            if args[0]=='ls':return 'broken\ncollector_1403_test'
            if '/broken/' in args[1]:return 'cat: No such file'
            return json.dumps({'project_remote_id':1403,'name':'Test'})
        self.assertEqual(1403,choose_project(read_projects(shell),'1403')['id'])
    def test_project_snapshot_carries_policy_without_accounts_or_features(self):
        def shell(*args):
            if args[1].endswith('map.ngm'):return json.dumps({'layers':[{'path':'layer_test'}]})
            return json.dumps({'name':'Field points','geometry_type':1,'is_editable':False,
                'collector_editable':True,'sync_direction':3,'layer_origin':{'managed_by_project':True},
                'account':'secret-account','password':'secret-password','features':[{'coordinates':[1,2]}]})
        snapshot=snapshot_project(shell,self.projects[0]);text=json.dumps(snapshot)
        self.assertTrue(snapshot['layers'][0]['expected_editable'])
        for forbidden in ('secret-account','secret-password','coordinates','features'):
            self.assertNotIn(forbidden,text)
