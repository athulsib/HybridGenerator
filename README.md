# HybridGenerator ⚡️🌍

A hybrid-mode Minecraft server plugin that pre-generates spare worlds, compresses them, and supplies them to other servers via a shared database request/response table.

Never properly finished or tested in production, but the core functionality works from what i remember. Project was abandoned. 

## What it is
- Originially meant to be a provider for battle-royale setups that offloads world generation to a dedicated server.
- Supplies ready-made worlds as compressed BLOBs to remote servers that insert `PENDING` requests.

## How it works (high level)
1. On startup the plugin reads its config and opens a HikariCP MySQL connection.
2. It ensures a requests table exists (prefix from config + `world_requests`).
3. It generates a pool of default worlds, primes border chunks (via `ChunkyAPI`), then unloads them to save memory.
4. A background task polls the DB for rows where `status = 'PENDING'` (oldest first).
5. For each request:
    - Pick a random available world from the local list.
    - Zip the world folder, read the bytes, then Zstd-compress the zip.
    - Update the DB row with `world_name`, the compressed BLOB (`compressed_world`), set `status='PROCESSED'` and `response_time = NOW()`.
    - Delete the served world folder and remove it from the local list so the pool rotates.
6. If no worlds are available the plugin regenerates the default pool.

## Key components
- `HybridMode` \- lifecycle, generation loop, compression and DB update logic.
- `DatabaseUtils` \- Hikari connection and table creation.
- `FileUtils` \- zip/unzip and safe file operations.
- `WorldUtil` / `ChunkyAPI` \- world creation, chunk priming and unload helpers.

## Important notes & caveats
- Worlds are zipped then Zstd-compressed and stored as a BLOB in the DB.
- Table name is dynamic using the configured `database.table_prefix`.
- Worlds are deleted after being served; the provider keeps a rotating pool.
- Current implementation polls very frequently and contains a `wait(1000)` call and redundant `getConnection()` checks that should be reviewed.
