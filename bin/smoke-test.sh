PORT=$(shuf -i 1024-65535 -n 1)
lein do clean, shadow release app, run $PORT
