#!/bin/bash

# Configuration
HOST="localhost"
PORT=5140
NB_CLIENTS=5
LOGS_PAR_CLIENT=20
DELAI=0.1  # 100ms entre les logs

echo "Début du test de charge - ${NB_CLIENTS} clients envoyant ${LOGS_PAR_CLIENT} logs chacun"

# Lancer le serveur en arrière-plan si nécessaire
# java -jar target/server_centralise-*.jar &

# Fonction pour un client
function client_thread {
    local client_id=$1
    for ((j=1; j<=$LOGS_PAR_CLIENT; j++)); do
        timestamp=$(date +"%Y-%m-%d %T")
        log_message="Client $client_id - Log $j - $timestamp"
        
        # Envoi avec nc et gestion d'erreur
        if ! echo "$log_message" | nc -w 1 $HOST $PORT; then
            echo "ERREUR: Client $client_id - Échec envoi log $j" >&2
        fi
        
        sleep $DELAI
    done
}

# Lancer les clients en parallèle
for ((i=1; i<=$NB_CLIENTS; i++)); do
    client_thread $i &
    pids[$i]=$!
done

# Attendre la fin de tous les clients
for pid in ${pids[*]}; do
    wait $pid
done

echo "Test terminé - Tous les clients ont fini"
echo "Vérifiez les logs côté serveur pour le résultat"
