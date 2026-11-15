import { PGlite } from '@electric-sql/pglite';
import fs from 'fs';
const root = new URL('../opsmind-core/src/main', import.meta.url).pathname;
const migration = fs.readFileSync(`${root}/resources/db/migration/V9__fault_experiments.sql`, 'utf8');
const java = fs.readFileSync(`${root}/java/com/opsmind/core/faultlab/FaultLabService.java`, 'utf8');
const watchdogSql = java.match(/"(SELECT id, target_service FROM fault_experiments WHERE status='RUNNING' AND started_at \+ make_interval[^"]*)"/)[1];
const db = new PGlite();
let pass = 0, fail = 0;
const ok = (name, cond, extra='') => { cond ? pass++ : fail++; console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const rejects = async (sql, params=[]) => { try { await db.query(sql, params); return null; } catch (e) { return e; } };
const ORG = '11111111-1111-1111-1111-111111111111', USR = '22222222-2222-2222-2222-222222222222';
const ins = (svc, status='RUNNING', dur=30, extra='') => db.query(
  `INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status${extra ? ',started_at' : ''}) VALUES ($1,$2,'k',$3,$4,$5${extra ? ',now() - interval \'200 seconds\'' : ''}) RETURNING id`, [ORG, USR, svc, dur, status]);

await db.exec(migration);
ok('V9 migration applies on real PostgreSQL', true);

let e = await rejects(`INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status) VALUES ('${ORG}','${USR}','k','payment',0,'RUNNING')`);
ok('CHECK: duration 0 rejected', e && /check/i.test(e.message));
e = await rejects(`INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status) VALUES ('${ORG}','${USR}','k','payment',121,'RUNNING')`);
ok('CHECK: duration 121 (> 120 max) rejected', e && /check/i.test(e.message));
e = await rejects(`INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status) VALUES ('${ORG}','${USR}','k','payment',30,'BOGUS')`);
ok('CHECK: unknown status rejected', e && /check/i.test(e.message));
const a = await ins('payment', 'RUNNING', 120);
ok('duration 120 accepted; gen_random_uuid default works', a.rows.length === 1 && a.rows[0].id.length === 36);

e = await rejects(`INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status) VALUES ('${ORG}','${USR}','k','payment',30,'RUNNING')`);
ok('unique index: second RUNNING on same service rejected', e && e.code === '23505', `(sqlstate ${e && e.code})`);
ok('unique index: RUNNING on a different service allowed', (await ins('inventory')).rows.length === 1);

// Concurrent starts on one service: exactly one may win (constraint-level; PGlite serializes statements).
const race = await Promise.allSettled(Array.from({ length: 8 }, () => ins('order')));
ok('8 concurrent starts on one service -> exactly 1 succeeds', race.filter(r => r.status === 'fulfilled').length === 1,
   `(fulfilled=${race.filter(r => r.status === 'fulfilled').length})`);

await db.query(`UPDATE fault_experiments SET status='STOPPED', ended_at=now() WHERE id=$1`, [a.rows[0].id]);
ok('after STOPPED a new RUNNING on that service is allowed', (await ins('payment')).rows.length === 1);

// DISABLE_UNCONFIRMED blocks new experiments until confirmed.
await db.query(`UPDATE fault_experiments SET status='DISABLE_UNCONFIRMED' WHERE target_service='payment' AND status='RUNNING'`);
e = await rejects(`INSERT INTO fault_experiments(organization_id,started_by,fault_key,target_service,duration_seconds,status) VALUES ('${ORG}','${USR}','k','payment',30,'RUNNING')`);
ok('DISABLE_UNCONFIRMED blocks a new RUNNING on that service', e && e.code === '23505');

// Emergency-stop update, using the same shape as the service (IN ACTIVE).
await db.query(`UPDATE fault_experiments SET status='STOPPED', status_detail='emergency stop', ended_at=COALESCE(ended_at, now()) WHERE status IN ('RUNNING','DISABLE_UNCONFIRMED') AND target_service='payment'`);
ok('emergency confirm clears DISABLE_UNCONFIRMED -> STOPPED', (await db.query(`SELECT count(*)::int n FROM fault_experiments WHERE target_service='payment' AND status IN ('RUNNING','DISABLE_UNCONFIRMED')`)).rows[0].n === 0);
ok('...and new experiments can start again', (await ins('payment')).rows.length === 1);

// Watchdog query, extracted from the Java source.
await db.query(`DELETE FROM fault_experiments`);
const overdue = await ins('payment', 'RUNNING', 30, 'past');   // started 200s ago, 30s long
const fresh = await ins('inventory', 'RUNNING', 120);           // just started
const due = (await db.query(watchdogSql)).rows.map(r => r.id);
ok('watchdog SQL selects the overdue experiment', due.includes(overdue.rows[0].id));
ok('watchdog SQL ignores the fresh experiment', !due.includes(fresh.rows[0].id));

// Startup-sweep shape: RUNNING rows -> EXPIRED / DISABLE_UNCONFIRMED.
await db.query(`UPDATE fault_experiments SET status='DISABLE_UNCONFIRMED', status_detail='cleared by startup sweep', ended_at=now() WHERE status='RUNNING' AND target_service='payment'`);
const st = (await db.query(`SELECT status FROM fault_experiments WHERE target_service='payment'`)).rows[0].status;
ok('startup sweep with no token leaves DISABLE_UNCONFIRMED (never claims success)', st === 'DISABLE_UNCONFIRMED');

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
