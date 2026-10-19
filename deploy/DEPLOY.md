# Deploying Big 2 to a Linux server (e.g. Tencent Cloud Lighthouse)

This puts the game at `https://big2.yourdomain.com`, behind nginx with a free Let's Encrypt certificate from certbot. It works next to other nginx sites: setup only adds its own `big2` site file.

The game runs as a `big2` service on `127.0.0.1:18090`; it starts on boot and restarts if it crashes.

## 1. Point your domain at the server

At your DNS provider add an `A` record: name `big2`, value = your server's public IP. Check from Windows: `nslookup big2.yourdomain.com`.

Servers in mainland China regions also need ICP filing for the domain; Hong Kong, Singapore and other overseas regions don't.

## 2. Open the firewall

Lighthouse console → your server → **Firewall**: allow TCP **80** and **443**. Port 18090 stays closed; nginx reaches the game locally.

## 3. Download, build and set up (on the server)

The repository must be public, or the server needs access to it.

```bash
sudo apt-get update && sudo apt-get install -y git openjdk-21-jdk-headless maven
```

```bash
git clone https://github.com/Morker/big2.git ~/big2 && cd ~/big2 && mvn -q -B package -DskipTests && cp big2-server/target/big2-server.jar deploy/
```

```bash
cd ~/big2/deploy && sudo bash setup.sh big2.yourdomain.com
```

When it prints `Big 2 is running`, open `https://big2.yourdomain.com`.

## Updating later

Push your changes to GitHub, then on the server:

```bash
bash ~/big2/deploy/redeploy.sh
```

It pulls, builds and restarts the game. Games in progress end when it restarts, so update between games.

## When something's wrong

| Problem | Check |
|---|---|
| Page doesn't load | DNS (`nslookup`), the firewall (80 and 443) |
| Certificate error | `sudo certbot certificates`, and that DNS points at this server |
| "502 Bad Gateway" or "Could not reach the game server" | `sudo systemctl status big2` and `sudo journalctl -u big2 -n 50` |
| nginx errors | `sudo nginx -t` and `sudo tail -n 30 /var/log/nginx/error.log` |
| Watch the game's log live | `sudo journalctl -u big2 -f` |

To remove the game: `sudo systemctl disable --now big2`, `sudo rm /etc/nginx/sites-enabled/big2`, `sudo systemctl reload nginx`.
