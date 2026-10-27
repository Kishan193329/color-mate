"""RunPod control for the GPU workflow (.github/workflows/gpu-job.yml). Nothing secret in here:
RUNPOD_API_KEY and RUNPOD_SSH_PUBKEY come from the environment (GitHub secrets, or on the PC
`~/.runpod_key` / `~/.ssh/runpod_ed25519.pub`).

    python runpod.py create [GPU...]      tries each GPU type on SECURE then COMMUNITY -> prints id
    python runpod.py wait ID              prints "ip port" once ssh is reachable
    python runpod.py stop ID
    python runpod.py status
"""
import json
import os
import sys
import time
import urllib.request

GPUS = ["NVIDIA GeForce RTX 4090", "NVIDIA RTX A6000", "NVIDIA L40S", "NVIDIA A40"]
IMAGE = "runpod/pytorch:2.4.0-py3.11-cuda12.4.1-devel-ubuntu22.04"


def _key():
    k = os.environ.get("RUNPOD_API_KEY")
    if not k:
        k = open(os.path.expanduser("~/.runpod_key")).read()
    return k.strip()


def _pubkey():
    k = os.environ.get("RUNPOD_SSH_PUBKEY")
    if not k:
        k = open(os.path.expanduser("~/.ssh/runpod_ed25519.pub")).read()
    return k.strip()


def gql(query, variables=None):
    req = urllib.request.Request("https://api.runpod.io/graphql",
                                 data=json.dumps({"query": query, "variables": variables or {}}).encode(),
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer " + _key(),
                                          "User-Agent": "colourmate"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def create(gpus, disk):
    for gpu in gpus:
        for cloud in ("SECURE", "COMMUNITY"):
            r = gql('mutation($in: PodFindAndDeployOnDemandInput){ podFindAndDeployOnDemand(input:$in){ id costPerHr } }',
                    {"in": {"cloudType": cloud, "gpuCount": 1, "gpuTypeId": gpu, "name": "colourmate-gpu-job",
                            "imageName": IMAGE, "containerDiskInGb": disk, "volumeInGb": 0, "ports": "22/tcp",
                            "startSsh": True, "supportPublicIp": True,
                            "env": [{"key": "PUBLIC_KEY", "value": _pubkey()}]}})
            pod = (r.get("data") or {}).get("podFindAndDeployOnDemand")
            if pod:
                print(f"{gpu} {cloud} ${pod['costPerHr']}/hr", file=sys.stderr)
                return pod["id"]
    raise SystemExit("no GPU available: " + ", ".join(gpus))


def ssh_addr(pid):
    p = gql('query($id:String!){ pod(input:{podId:$id}) { runtime { ports { ip isIpPublic privatePort publicPort } } } }',
            {"id": pid})["data"]["pod"]
    for x in ((p or {}).get("runtime") or {}).get("ports") or []:
        if x["privatePort"] == 22 and x["isIpPublic"]:
            return f'{x["ip"]} {x["publicPort"]}'
    return ""


def main():
    cmd = sys.argv[1]
    if cmd == "create":
        gpus = [g for g in sys.argv[2:] if g.strip()] or GPUS
        print(create(gpus, int(os.environ.get("POD_DISK", "60"))))
    elif cmd == "wait":
        for _ in range(90):
            a = ssh_addr(sys.argv[2])
            if a:
                print(a)
                return
            time.sleep(10)
        raise SystemExit("pod never exposed ssh")
    elif cmd == "stop":
        print(gql('mutation($id:String!){ podTerminate(input:{podId:$id}) }', {"id": sys.argv[2]}))
    elif cmd == "status":
        print(json.dumps(gql("query { myself { clientBalance currentSpendPerHr pods { id name desiredStatus costPerHr } } }")))


if __name__ == "__main__":
    main()
