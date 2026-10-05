# Opzioni per il servizio prezzi

Verifica 5 ottobre 2026. Nessun hosting o costo attivato.
Il servizio locale richiede Python3.12, un disco persistente per SQLite,
un processo API HTTPS e un job giornaliero nello stesso archivio.
Per la prima versione un singolo processo e 512 MB–1 GB di RAM sono requisiti
di partenza progettuali, da misurare sul catalogo validato.

## Opzioni concrete

- Server/NAS gia' disponibile: nessun nuovo canone hosting, ma deve restare
  acceso, raggiungibile e amministrato. HTTPS, backup e job giornaliero
  richiedono configurazione. Hardware e rete dell'utente non ancora indicati.
- [Render](https://render.com/pricing): compute Starter 7 USD/mese;
  disco persistente 0.25 USD/GB/mese. Per 1 GB: almeno 7.25 USD/mese,
  esclusi tasse, traffico oltre franchigia e altri servizi. HTTPS gestito.
  Il job deve accedere al disco del processo API: un cron separato non
  condivide automaticamente SQLite. Configurare pianificazione nello stesso
  processo oppure scegliere un database condiviso (ulteriore modifica).
- [Hetzner, tariffa dal 15 giugno 2026](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/):
  nuovi CX23 in Germania/Finlandia 5.49 EUR/mese esclusi IVA e IPv4
  (vecchi piani 3.99); verificare tabella per localita' e IP prima di ordinare.
  Server Linux gestibile con API, job giornaliero e SQLite sullo stesso disco;
  HTTPS, aggiornamenti e backup restano da amministrare. Nessun ordine.

[Render gratuito](https://render.com/docs/free) non conserva SQLite dopo
riavvio/sospensione, non supporta disco persistente e si sospende dopo15min
di inattivita': utilizzabile per anteprima, insufficiente per questo archivio.

Prima di pubblicare restano necessarie almeno due fonti riutilizzabili e
validate. Acquistare hosting non risolve l'accesso ai listini.
