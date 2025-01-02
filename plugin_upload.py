import requests

# Pterodactyl API details
PANEL_URL = "https://panel.lilypad.gg"  # Replace with your panel URL
SERVER_ID = "c69a0091-a46b-45bd-b7c1-3e305c672c51"  # Replace with your server UUID
API_KEY = "ptlc_rZQBFlnBCwbRXJilo5WrnQBMWVaK604LGjD8ffMTpk4"  # Replace with your API key
PLUGINS_FOLDER = "./plugins/"
LOCAL_FILE_PATH = "./target/Mai-1.0.3.jar"  # Replace with the path to your local file

# Headers for API requests
HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Accept": "application/json",
}

def list_files():
    """List files in the plugins directory."""
    url = f"{PANEL_URL}/api/client/servers/{SERVER_ID}/files/list"
    params = {"directory": PLUGINS_FOLDER}
    response = requests.get(url, headers=HEADERS, params=params)
    if response.status_code == 200:
        return response.json()["data"]
    else:
        print(f"Error listing files: {response.status_code} {response.text}")
        return []

def delete_file(file_name):
    """Delete a specific file."""
    url = f"{PANEL_URL}/api/client/servers/{SERVER_ID}/files/delete"
    payload = {"root": PLUGINS_FOLDER, "files": [file_name]}
    response = requests.post(url, headers=HEADERS, json=payload)
    if response.status_code == 204:
        print(f"Deleted: {file_name}")
    else:
        print(f"Error deleting file: {file_name} {response.status_code} {response.text}")

def get_presigned_url():
    """Retrieve the pre-signed upload URL."""
    url = f"{PANEL_URL}/api/client/servers/{SERVER_ID}/files/upload"
    params = {"directory": PLUGINS_FOLDER}
    response = requests.get(url, headers=HEADERS, params=params)
    if response.status_code == 200:
        return response.json()["attributes"]["url"]
    else:
        print(f"Error getting pre-signed URL: {response.status_code} {response.text}")
        return None

def upload_file_to_presigned_url(presigned_url):
    """Upload the file to the pre-signed URL."""
    with open(LOCAL_FILE_PATH, "rb") as file:
        response = requests.put(presigned_url, data=file)  # Use PUT for binary upload
    if response.status_code in [200, 204]:
        print("File uploaded successfully.")
    else:
        print(f"Error uploading file: {response.status_code} {response.text}")


def main():
    # Step 1: List files in the plugins folder
    files = list_files()

    # Step 2: Delete all .jar files containing "Mai-"
    for file in files:
        if "Mai-" in file["attributes"]["name"] and file["attributes"]["name"].endswith(".jar"):
            delete_file(file["attributes"]["name"])

    # Step 3: Get the pre-signed URL
    presigned_url = get_presigned_url()
    if not presigned_url:
        return

    # Step 4: Upload the new file
    upload_file_to_presigned_url(presigned_url)

if __name__ == "__main__":
    main()
