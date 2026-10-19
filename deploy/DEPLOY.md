# Deploying Big 2 to Tencent Cloud Lighthouse

This puts the game at `https://big2.yourdomain.com`. Caddy handles HTTPS certificates automatically, and the game runs as a service that starts on boot and restarts if it crashes.

Replace `big2.yourdomain.com` with your real (sub)domain and `SERVER_IP` with your Lighthouse public IP throughout.

## 1. Point your domain at the server

At your DNS provider (Tencent Cloud DNSPod or wherever the domain lives), add a record:

| Type | Name | Value |
|---|---|---|
| A | `big2` | `SERVER_IP` |

Check it from Windows after a few minutes: `nslookup big2.yourdomain.com` should show `SERVER_IP`.

> **Mainland China region?** Websites on ports 80/443 in mainland regions need the domain to have ICP filing (备案), or the traffic gets blocked. Hong Kong, Singapore and other overseas regions don't need it.

## 2. Open the firewall

Lighthouse console → your server → **Firewall** → add rules:

| Protocol | Port | Source |
|---|---|---|
| TCP | 80 | All IPv4 |
| TCP | 443 | All IPv4 |

Port 22 (SSH) is usually already open. You do **not** need to open 8090.

## 3a. Easiest: build on the server from GitHub

Needs the repository to be public. In the server terminal:

```bash
sudo apt-get update && sudo apt-get install -y git openjdk-21-jdk-headless maven
```

```bash
git clone https://github.com/Morker/big2.git ~/big2 && cd ~/big2 && mvn -q -B package -DskipTests && cp big2-server/target/big2-server.jar deploy/
```

Then run the setup from the `deploy` folder:

```bash
cd ~/big2/deploy && sudo bash setup.sh big2.yourdomain.com
```

To update later: push your changes to GitHub, then on the server run `bash ~/big2/deploy/redeploy.sh`.

## 3b. Or: build on your PC and upload

On your PC, from the project folder:

```bash
mvn clean install
```

Then copy the three files to the server. The default Lighthouse Ubuntu user is `ubuntu`:

```bash
ssh ubuntu@SERVER_IP "mkdir -p ~/big2"
```

```bash
scp big2-server/target/big2-server.jar deploy/setup.sh deploy/update.sh deploy/big2.service ubuntu@SERVER_IP:~/big2/
```

If you don't use SSH from your PC, the Lighthouse console's web terminal (OrcaTerm) can upload files too.

## 4. Run the setup (once)

Log in to the server (`ssh ubuntu@SERVER_IP`, or OrcaTerm), then:

```bash
cd ~/big2 && sudo bash setup.sh big2.yourdomain.com
```

When it prints `Big 2 is running`, open `https://big2.yourdomain.com`, create a room and send the invite link to your friends.

## Updating later

Build on your PC, upload the new jar, and restart:

```bash
scp big2-server/target/big2-server.jar ubuntu@SERVER_IP:~/big2/
```

```bash
ssh ubuntu@SERVER_IP "cd ~/big2 && sudo bash update.sh"
```

Games in progress end when the server restarts, so update between games.

## When something's wrong

| Problem | Check |
|---|---|
| Page doesn't load at all | DNS (`nslookup`), the Lighthouse firewall (80 and 443), and ICP filing for mainland regions |
| Browser warns about the certificate | Caddy couldn't get one yet. See `sudo journalctl -u caddy -n 50`; usually DNS or the firewall |
| Page loads but "Could not reach the game server" | `sudo systemctl status big2` and `sudo journalctl -u big2 -n 50` |
| Watch the game's log live | `sudo journalctl -u big2 -f` |
