import urllib.request
import urllib.error
import json
import base64

client_id = "client_01KRQFQKMXYQK09HJ9TCRGDTE3"
secret = "18cf97ad0a8f064cf689af820e89e117cc56b2a4ea7b341e070f68fc0dfb51be"
# In oauth flow with code, it gives token for the specific user

# Just simulate getting auth code is hard without browser.
# But wait, the app gets an auth_code then exchanges it. The exchange gives an access_token.
# Then the app uses this access_token for API calls.

# The user is doing `client_credentials` which is a machine-to-machine flow.
# Smartcar allows fetching vehicle lists with machine token?
# No, usually access token from client_credentials is just for specific admin tasks or
# webhook mgmt? Wait, Smartcar documentation says vehicle access needs user token (Authorization code grant)
# But wait, wait! The app itself does client_credentials?
