import urllib.request
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
    print("Token:", token)
    
    conn_url = "https://vehicle.api.smartcar.com/v3/connections"
    conn_req = urllib.request.Request(conn_url)
    conn_req.add_header("Authorization", f"Bearer {token}")
    try:
        conn_res = urllib.request.urlopen(conn_req)
        conn_data = json.loads(conn_res.read().decode())
        print("Connections:", json.dumps(conn_data, indent=2))
    except urllib.error.HTTPError as e:
        print("Error:", e.read().decode())

except urllib.error.HTTPError as e:
    print("Auth Error:", e.read().decode())
