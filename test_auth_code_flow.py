import urllib.request
import urllib.error
import json
import base64

client_id = "client_01KRQFQKMXYQK09HJ9TCRGDTE3"
secret = "18cf97ad0a8f064cf689af820e89e117cc56b2a4ea7b341e070f68fc0dfb51be"

url = "https://iam.smartcar.com/oauth2/token"
req = urllib.request.Request(url, method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
body = f"grant_type=client_credentials&client_id={client_id}&client_secret={secret}".encode("utf-8")
try:
    response = urllib.request.urlopen(req, data=body)
    data = json.loads(response.read().decode())
    token = data["access_token"]
    
    print("Testing /v3/vehicles with token...")
    v_req = urllib.request.Request("https://vehicle.api.smartcar.com/v3/vehicles")
    v_req.add_header("Authorization", f"Bearer {token}")
    try:
        v_res = urllib.request.urlopen(v_req)
        print("Vehicles response:", json.loads(v_res.read().decode()))
    except urllib.error.HTTPError as e:
        print("Vehicles Error:", e.read().decode())

except urllib.error.HTTPError as e:
    print("Auth Error:", e.read().decode())
