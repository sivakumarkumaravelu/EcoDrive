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
    'grant_type': 'authorization_code',
    'code': '553fa538-1f07-42f6-b9e8-7b0e8226872c',
    'redirect_uri': 'ecodrive://callback'
}).encode('ascii')

req = urllib.request.Request(url, data=data)
req.add_header('Authorization', f'Basic {b64_auth}')
req.add_header('Content-Type', 'application/x-www-form-urlencoded')

try:
    with urllib.request.urlopen(req) as response:
        print(response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(e.code)
    print(e.read().decode('utf-8'))
