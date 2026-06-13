import urllib.request
import urllib.parse
import base64
import json

client_id = 'fa9028be-a5c6-4c9b-8ca8-8289e90c701c'
client_secret = '9782e5a0bc2a85fa2c18d298b983fdebc5e0d3b041c7cc82cbac9fa28a5cad34'

auth_str = f"{client_id}:{client_secret}"
b64_auth = base64.b64encode(auth_str.encode('ascii')).decode('ascii')

url = 'https://auth.smartcar.com/oauth/token'
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
            
            print("--- v3.0 request (the app logic) ---")
            try:
                v3_req = urllib.request.Request(f"https://vehicle.api.smartcar.com/v3/vehicles/{vehicle_id}")
                v3_req.add_header('Authorization', f'Bearer {token}')
                with urllib.request.urlopen(v3_req) as v3_resp:
                    print(v3_resp.read().decode('utf-8'))
            except urllib.error.HTTPError as e:
                print(e.code, e.read().decode('utf-8'))

except urllib.error.HTTPError as e:
    print(e.code)
    print(e.read().decode('utf-8'))

