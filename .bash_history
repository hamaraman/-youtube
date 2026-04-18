sudo apt update
sudo apt install nodejs npm -y
sudo apt install mysql-server -y
sudo npm install -g pm2
sudo apt install unzip -y
unzip MyTube.zip
cd MyTube
npm install
pm2 start server.js
sudo iptables -I INPUT 1 -p tcp --dport 8080 -j ACCEPT
pm2 logs
pm2 delete server
rm -rf node_modules package-lock.json
npm install
npm install express
pm2 start server.js
pm2 logs
pm2 delete server
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo dpkg --configure -a
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
pm2 delete server
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
install N|solid Runtime, run: apt install nsolid -y
sudo apt-get install -y nodejs
sudo apt-get remove --purge -y nodejs npm libnode-dev
sudo apt-get autoremove -y
sudo apt-get install -y nodejs
npm install
pm2 start server.js
sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d coding-tube.duckdns.org
sudo rm /etc/nginx/sites-enabled/default
sudo systemctl restart nginx
sudo nginx -t
pm2 list
sudo nginx -t
sudo ln -s /etc/nginx/sites-available/coding-tube /etc/nginx/sites-enabled/
sudo systemctl reload nginx
sudo iptables -F
sudo iptables -X
sudo iptables -t nat -F
sudo iptables -t nat -X
sudo iptables -t mangle -F
sudo iptables -t mangle -X
sudo iptables -P INPUT ACCEPT
sudo iptables -P FORWARD ACCEPT
sudo iptables -P OUTPUT ACCEPT
sudo apt-get install iptables-persistent -y
sudo netfilter-persistent save
sudo netfilter-persistent reload
sudo iptables -F
sudo iptables -P INPUT ACCEPT
sudo iptables -P FORWARD ACCEPT
sudo iptables -P OUTPUT ACCEPT
sudo iptables-save | sudo tee /etc/iptables/rules.v4
sudo systemctl restart nginx
sudo ufw disable
cat /etc/nginx/sites-available/coding-tube
sudo nano /etc/nginx/sites-available/coding-tube
sudo ln -s /etc/nginx/sites-available/coding-tube /etc/nginx/sites-enabled/
sudo systemctl restart nginx
ls /etc/nginx/sites-enabled/
sudo certbot --nginx -d coding-tube.duckdns.org
ls
cd mytube
ls
git init
git remote add origin [https://github.com/hamaraman/mytube]
git fetch
git reset --hard origin/master
git reset --hard origin/main
git fetch
git remote remove origin
git remote add origin https://github.com/hamaraman/mytube.git
git fetch
git reset --hard origin/master
pm2 restart server
sudo apt update
cd mytube
sudo apt update
sudo sed -i 's/ap-chuncheon-1-ad-1.clouds.archive.ubuntu.com/mirror.kakao.com/g' /etc/apt/sources.list
sudo sed -i 's/archive.ubuntu.com/mirror.kakao.com/g' /etc/apt/sources.list
sudo sed -i 's/security.ubuntu.com/mirror.kakao.com/g' /etc/apt/sources.list
sudo apt update
sudo rm -rf /var/lib/apt/lists/*
sudo apt clean
sudo sed -i 's/mirror.kakao.com/ftp.kaist.ac.kr/g' /etc/apt/sources.list
sudo apt update
sudo apt install ffmpeg -y
cd mytube
sudo apt install ffmpeg -y
sudo kill -9 16453
sudo rm /var/lib/dpkg/lock-frontend
sudo rm /var/lib/dpkg/lock
sudo rm /var/cache/apt/archives/lock
sudo dpkg --configure -a
sudo apt install ffmpeg -y
cd mytube
sudo sed -i '/^UseDNS/d' /etc/ssh/sshd_config
echo "UseDNS no" | sudo tee -a /etc/ssh/sshd_config
sudo systemctl restart ssh
sudo dpkg --configure -a
sudo apt install ffmpeg -y
sudo rm -rf /var/lib/apt/lists/*
sudo rm -f /var/cache/apt/*.bin
sudo apt clean
sudo apt update
sudo mv /etc/apt/sources.list.d/nodesource.list ~/nodesource.list.backup
cd youtube
ls
git pull
git clone https://github.com/hamaraman/-youtube.git mytube_v2
cp -r uploads mytube_v2/
cd mytube_v2
npm install
node server.js
cp ../server.js .
cp ../database.json .
node server.js
cp ../index.html .
cp -r ../public .
node server.js
sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 1 -p tcp --dport 8080 -j ACCEPT
node server.js
sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT
sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080
sudo apt-get install iptables-persistent -y
sudo netfilter-persistent save
sudo netfilter-persistent reload
node server.js
sudo npm install pm2 -g
pm2 start server.js
pm2 save
pm2 list
pwd
git pull origin main
git init
git remote add origin https://github.com/hamaraman/mytube.git
git fetch --all
git reset --hard origin/main
pm2 restart server.js
git clone https://github.com/hamaraman/mytube.git
git clone https://github.com/hamaraman/mytube.git mytube_final
cd mytube_final
cp ../mytube_v2/server.js .
cp ../mytube_v2/database.json .
git add .
git commit -m "화면 구분 코드 수정"
git config --global user.email "haram61423@gmail.com"
git config --global user.name "hamaraman"
git commit -m "화면 구분 코드 수정"
git push origin master
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
git push origin master
git config --global credential.helper store
git add .
git commit -m "모바일 PC 화면 구분 및 디자인 수정"
git push origin master
cd ~/mytube_final
git pull origin master
pm2 restart server.js
pm2 list
pm2 restart server
