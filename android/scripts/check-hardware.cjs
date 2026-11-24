const cp = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const args = process.argv.slice(2);
if (!args[0] || !/^emulator-\d+$/.test(args[0])) {
    console.error('Usage: node android/scripts/check-hardware.cjs emulator-SERIAL [all|camera|warehouse|navigation]');
    console.error('Build/install hardwareTest and its androidTest APKs first with -PhardwareTestBuild.');
    process.exit(2);
}
const serial = args[0];
const scenario = args[1] || 'all';
if (!['all', 'camera', 'warehouse', 'navigation'].includes(scenario)) throw Error('Unknown scenario');
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
const adb = process.env.ADB || (sdk ? path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb');
const target = 'com.lastpaycheck.hardwaretest';
const runner = target + '.test/androidx.test.runner.AndroidJUnitRunner';
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'last-paycheck-hardware-'));
const command = (...commandArgs) => cp.execFileSync(adb, ['-s', serial, ...commandArgs], {
    encoding: 'utf8', timeout: 10_000, stdio: ['ignore', 'pipe', 'pipe']
});
if (!command('shell', 'pm', 'path', target).startsWith('package:')) throw Error('Isolated APK absent');
if (!command('shell', 'pm', 'path', target + '.test').startsWith('package:')) throw Error('Test APK absent');
console.log('Isolated package:', target, 'Device:', serial, 'Logs:', output);

async function run(name, className, motion = false) {
    const log = fs.createWriteStream(path.join(output, name + '.log'));
    let result = '', last = '';
    const child = cp.spawn(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-e', 'class', className, runner]);
    child.stdout.on('data', data => { result += data; log.write(data); process.stdout.write(data); });
    child.stderr.on('data', data => log.write(data));
    const timer = motion ? setInterval(() => {
        try {
            const request = command('exec-out', 'run-as', target, 'cat', 'files/motion-request.txt').trim();
            if (request === last || !/^\d+\|[-\d.]+:[-\d.]+:[-\d.]+$/.test(request)) return;
            const reply = command('emu', 'sensor', 'set', 'acceleration', request.split('|')[1]);
            if (!reply.includes('OK')) throw Error('Virtual accelerometer rejected sample');
            last = request;
            log.write('\nSENSOR ' + request + '\n');
        } catch (error) {
            if (error.status === undefined) console.error(error.message);
        }
    }, 150) : null;
    try {
        await new Promise((resolve, reject) => {
            child.once('error', reject);
            child.once('exit', code => code === 0 ? resolve() : reject(Error('ADB exit ' + code)));
        });
        if (!/OK \(\d+ tests?\)/.test(result) || result.includes('FAILURES!!!')) throw Error(name + ' failed; see ' + output);
    } finally {
        if (timer) clearInterval(timer);
        log.end();
    }
}

(async () => {
    if (scenario === 'all' || scenario === 'camera') {
        await run('camera', 'com.lastpaycheck.app.CameraSettingsScenarioTest');
    }
    if (scenario === 'all' || scenario === 'warehouse') {
        await run('warehouse-lifecycle', 'com.lastpaycheck.app.TiltWarehouseScenarioTest#aTiltHoldModesBackgroundAndRotationPreserveTheActiveShift', true);
        command('shell', 'am', 'force-stop', target);
        await run('warehouse-restore', 'com.lastpaycheck.app.TiltWarehouseScenarioTest#bContinueAfterProcessDeathCompletesTheTiltShiftAndPaysOnce', true);
        command('emu', 'sensor', 'set', 'acceleration', '0:0:9.80665');
    }
    if (scenario === 'all' || scenario === 'navigation') {
        await run('navigation', 'com.lastpaycheck.app.GameNavigationTest,com.lastpaycheck.app.HardwareRegressionTest');
    }
    console.log('PASS. Screenshots: /sdcard/Android/data/' + target + '/files');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
