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

conn_url = "https://vehicle.api.smartcar.com/v3/connections"
conn_req = urllib.request.Request(conn_url)
conn_req.add_header("Authorization", f"Bearer {token}")
conn_res = urllib.request.urlopen(conn_req)
conn_data = json.loads(conn_res.read().decode())

vid = conn_data["data"][0]["relationships"]["vehicle"]["data"]["id"]
print("Vehicle ID:", vid)

fuel_url = f"https://vehicle.api.smartcar.com/v3/vehicles/{vid}/fuel"
fuel_req = urllib.request.Request(fuel_url)
fuel_req.add_header("Authorization", f"Bearer {token}")

try:
    fuel_res = urllib.request.urlopen(fuel_req)
    print("Fuel:", json.loads(fuel_res.read().decode()))
except urllib.error.HTTPError as e:
    print("Fuel Error:", e.read().decode())

try:
    print("\nTrying with sc-user-id header...")
    user_id = conn_data["data"][0]["relationships"]["user"]["data"]["id"]
    fuel_req2 = urllib.request.Request(fuel_url)
    fuel_req2.add_header("Authorization", f"Bearer {token}")
    fuel_req2.add_header("sc-user-id", user_id)
    fuel_res2 = urllib.request.urlopen(fuel_req2)
    print("Fuel 2:", json.loads(fuel_res2.read().decode()))
except urllib.error.HTTPError as e:
    print("Fuel 2 Error:", e.read().decode())

