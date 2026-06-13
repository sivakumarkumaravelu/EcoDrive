import urllib.request
import urllib.parse
import base64
import json

client_id = 'client_01KRQFQKMXYQK09HJ9TCRGDTE3' # from PreferenceManager
client_secret = '9782e5a0bc2a85fa2c18d298b983fdebc5e0d3b041c7cc82cbac9fa28a5cad34'

auth_str = f"{client_id}:{client_secret}"
b64_auth = base64.b64encode(auth_str.encode('ascii')).decode('ascii')

url = 'https://iam.smartcar.com/oauth2/token'
data = urllib.parse.urlencode({
    'grant_type': 'client_credentials'
}).encode('ascii')

req = urllib.request.Request(url, data=data)
req.add_header('Authorization', f'Basic {b64_auth}')
req.add_header('Content-Type', 'application/x-www-form-urlencoded')

try:
    with urllib.request.urlopen(req) as response:
        resp_str = response.read().decode('utf-8')
        token_data = json.loads(resp_str)
        token = token_data['access_token']
        print("Got token!")
        
        # Now fetch vehicle ID
        conn_req = urllib.request.Request("https://vehicle.api.smartcar.com/v3/connections")
        conn_req.add_header('Authorization', f'Bearer {token}')
        with urllib.request.urlopen(conn_req) as conn_resp:
            conn_data = json.loads(conn_resp.read().decode('utf-8'))
            vehicle_id = conn_data['data'][0]['relationships']['vehicle']['data']['id']
            print(f"Vehicle ID: {vehicle_id}")
            
            # Now fetch attributes via v2.0 vs v3
            print("--- v2.0 request ---")
            try:
                v2_req = urllib.request.Request(f"https://api.smartcar.com/v2.0/vehicles/{vehicle_id}")
                v2_req.add_header('Authorization', f'Bearer {token}')
                with urllib.request.urlopen(v2_req) as v2_resp:
                    print(v2_resp.read().decode('utf-8'))
            except urllib.error.HTTPError as e:
                print(e.code, e.read().decode('utf-8'))

            print("--- v3.0 request (the app logic) ---")
            try:
                # The app translates https://api.smartcar.com/v2.0/vehicles/id to https://vehicle.api.smartcar.com/v3/vehicles/id
                v3_req = urllib.request.Request(f"https://vehicle.api.smartcar.com/v3/vehicles/{vehicle_id}")
                v3_req.add_header('Authorization', f'Bearer {token}')
                with urllib.request.urlopen(v3_req) as v3_resp:
                    print(v3_resp.read().decode('utf-8'))
            except urllib.error.HTTPError as e:
                print(e.code, e.read().decode('utf-8'))

except urllib.error.HTTPError as e:
    print(e.code)
    print(e.read().decode('utf-8'))

