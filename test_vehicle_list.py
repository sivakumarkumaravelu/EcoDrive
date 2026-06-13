import urllib.request
import urllib.error
import json

client_id = "client_01KRQFQKMXYQK09HJ9TCRGDTE3"
secret = "18cf97ad0a8f064cf689af820e89e117cc56b2a4ea7b341e070f68fc0dfb51be"

url = "https://iam.smartcar.com/oauth2/token"
req = urllib.request.Request(url, method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
body = f"grant_type=client_credentials&client_id={client_id}&client_secret={secret}".encode("utf-8")
response = urllib.request.urlopen(req, data=body)
data = json.loads(response.read().decode())
token = data["access_token"]

v_url = "https://api.smartcar.com/v2.0/vehicles"
v_req = urllib.request.Request(v_url)
v_req.add_header("Authorization", f"Bearer {token}")
try:
    v_res = urllib.request.urlopen(v_req)
    print("Vehicles v2.0:", json.loads(v_res.read().decode()))
except urllib.error.HTTPError as e:
    print("Vehicles v2.0 Error:", e.read().decode())
