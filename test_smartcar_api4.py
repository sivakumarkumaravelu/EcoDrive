import urllib.request
import urllib.parse
import json

client_id = 'client_01KRQFQKMXYQK09HJ9TCRGDTE3' 
client_secret = '9782e5a0bc2a85fa2c18d298b983fdebc5e0d3b041c7cc82cbac9fa28a5cad34'

url = 'https://iam.smartcar.com/oauth2/token'
data = urllib.parse.urlencode({
    'grant_type': 'client_credentials',
    'client_id': client_id,
    'client_secret': client_secret
}).encode('ascii')

req = urllib.request.Request(url, data=data)
req.add_header('Content-Type', 'application/x-www-form-urlencoded')

try:
    with urllib.request.urlopen(req) as response:
        resp_str = response.read().decode('utf-8')
        token_data = json.loads(resp_str)
        token = token_data['access_token']
        print("Got token!")
        
        # Now fetch connections
        conn_req = urllib.request.Request("https://vehicle.api.smartcar.com/v3/connections")
        conn_req.add_header('Authorization', f'Bearer {token}')
        with urllib.request.urlopen(conn_req) as conn_resp:
            conn_data = json.loads(conn_resp.read().decode('utf-8'))
            vehicle_id = conn_data['data'][0]['relationships']['vehicle']['data']['id']
            print(f"Vehicle ID: {vehicle_id}")
            
            # Fetch attributes
            print("--- v3.0 attributes request ---")
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

