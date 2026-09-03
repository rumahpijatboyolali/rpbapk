const SHEET_NAME = 'Transaksi';
const OTHER_INCOME_SHEET_NAME = 'PemasukanLain';
const EXPENSE_SHEET_NAME = 'Pengeluaran';
const ADMIN_SHEET_NAME = 'Admin';
const SESSION_PREFIX = 'RPB_ADMIN_SESSION_';
const SESSION_TTL_SECONDS = 30 * 60;
const DEFAULT_ADMIN_USERNAME = 'admin';
const DEFAULT_ADMIN_PASSWORD = 'admin123';

function sha256Hex_(text) {
  const bytes = Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    String(text),
    Utilities.Charset.UTF_8
  );
  return bytes.map(function(b) {
    const v = (b < 0 ? b + 256 : b).toString(16);
    return v.length === 1 ? '0' + v : v;
  }).join('');
}

function getAdminSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(ADMIN_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(ADMIN_SHEET_NAME);
    sheet.getRange('A1:B1').setValues([['Username', 'PasswordHash']]);
    sheet.getRange('A1:B1').setFontWeight('bold');
    sheet.getRange('A2:B2').setValues([[DEFAULT_ADMIN_USERNAME, sha256Hex_(DEFAULT_ADMIN_PASSWORD)]]);
    sheet.setFrozenRows(1);
    sheet.autoResizeColumns(1, 2);
  }
  return sheet;
}

function adminLogin(username, password) {
  username = String(username || '').trim();
  password = String(password || '');
  if (!username || !password) return { success:false, message:'Username dan password wajib diisi.' };

  const sheet = getAdminSheet_();
  const lastRow = sheet.getLastRow();
  const rows = lastRow >= 2 ? sheet.getRange(2, 1, lastRow - 1, 2).getValues() : [];
  const hash = sha256Hex_(password);

  for (let i = 0; i < rows.length; i++) {
    const u = String(rows[i][0] || '').trim();
    const h = String(rows[i][1] || '').trim();
    if (u === username && h === hash) {
      const token = Utilities.getUuid() + Utilities.getUuid();
      const session = { username: username, expiresAt: Date.now() + SESSION_TTL_SECONDS * 1000 };
      CacheService.getScriptCache().put(
        SESSION_PREFIX + token,
        JSON.stringify(session),
        SESSION_TTL_SECONDS
      );
      return { success:true, username:username, token:token, expiresAt:session.expiresAt };
    }
  }
  return { success:false, message:'Username atau password salah.' };
}

function requireAdmin(token) {
  token = String(token || '');
  if (!token) throw new Error('Sesi login diperlukan.');
  const cache = CacheService.getScriptCache();
  const key = SESSION_PREFIX + token;
  const raw = cache.get(key);
  if (!raw) throw new Error('Sesi login telah berakhir. Silakan login kembali.');

  let session;
  try { session = JSON.parse(raw); } catch (e) {
    cache.remove(key);
    throw new Error('Sesi login tidak valid.');
  }
  if (!session.expiresAt || Date.now() >= Number(session.expiresAt)) {
    cache.remove(key);
    throw new Error('Sesi login telah berakhir. Silakan login kembali.');
  }

  // Sliding session: aktivitas memperpanjang 30 menit.
  session.expiresAt = Date.now() + SESSION_TTL_SECONDS * 1000;
  cache.put(key, JSON.stringify(session), SESSION_TTL_SECONDS);
  return session;
}

function adminLogout(token) {
  token = String(token || '');
  if (token) CacheService.getScriptCache().remove(SESSION_PREFIX + token);
  return { success:true };
}

function changeAdminPassword(token, oldPassword, newPassword) {
  const session = requireAdmin(token);
  if (!newPassword || String(newPassword).length < 8) {
    throw new Error('Password baru minimal 8 karakter.');
  }
  const sheet = getAdminSheet_();
  const lastRow = sheet.getLastRow();
  const rows = lastRow >= 2 ? sheet.getRange(2, 1, lastRow - 1, 2).getValues() : [];
  const oldHash = sha256Hex_(oldPassword || '');
  for (let i = 0; i < rows.length; i++) {
    if (String(rows[i][0]).trim() === session.username) {
      if (String(rows[i][1]).trim() !== oldHash) throw new Error('Password lama salah.');
      sheet.getRange(i + 2, 2).setValue(sha256Hex_(newPassword));
      return {success:true, message:'Password berhasil diubah.'};
    }
  }
  throw new Error('Akun admin tidak ditemukan.');
}


function doGet() {
  return HtmlService.createTemplateFromFile('Index')
    .evaluate()
    .setTitle('Rekap Penjualan Rumah Pijat Boyolali')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function setupSpreadsheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);

  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }

  if (sheet.getLastRow() === 0) {
    sheet.appendRow(['ID', 'Tanggal', 'Nama Pelanggan', 'Nominal', 'Dibuat']);
    sheet.setFrozenRows(1); sheet.getRange('A1:E1').setFontWeight('bold');
    sheet.getRange('B:B').setNumberFormat('dd/MM/yyyy'); sheet.getRange('D:D').setNumberFormat('#,##0');
  }
  setupCashflowSheet_(OTHER_INCOME_SHEET_NAME, 'Keterangan');
  setupCashflowSheet_(EXPENSE_SHEET_NAME, 'Keterangan');
  return 'Database siap digunakan.';
}

function setupCashflowSheet_(name, descriptionHeader) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(name);
  if (!sheet) sheet = ss.insertSheet(name);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(['ID', 'Tanggal', descriptionHeader, 'Nominal', 'Dibuat']);
    sheet.setFrozenRows(1); sheet.getRange('A1:E1').setFontWeight('bold');
    sheet.getRange('B:B').setNumberFormat('dd/MM/yyyy'); sheet.getRange('D:D').setNumberFormat('#,##0');
  }
  return sheet;
}

function getCashflowSheet_(name) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(name);
  if (!sheet) { setupCashflowSheet_(name, 'Keterangan'); sheet = ss.getSheetByName(name); }
  return sheet;
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);

  if (!sheet) {
    setupSpreadsheet();
    sheet = ss.getSheetByName(SHEET_NAME);
  }

  return sheet;
}

function formatNamaPelanggan_(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
    .map(function(word) { return word.charAt(0).toUpperCase() + word.slice(1); })
    .join(' ');
}

function addTransaction(data, token) {
  requireAdmin(token);
  if (!data || !data.tanggal || !data.nama || data.nominal === undefined) {
    throw new Error('Tanggal, nama pelanggan, dan nominal wajib diisi.');
  }

  const nominal = Number(data.nominal);
  if (!Number.isFinite(nominal) || nominal <= 0) {
    throw new Error('Nominal harus berupa angka lebih dari 0.');
  }

  const tanggal = new Date(data.tanggal + 'T00:00:00');
  if (isNaN(tanggal.getTime())) {
    throw new Error('Tanggal tidak valid.');
  }

  const sheet = getSheet_();
  const id = Utilities.getUuid();

  sheet.appendRow([
    id,
    tanggal,
    formatNamaPelanggan_(data.nama),
    nominal,
    new Date()
  ]);

  return { success: true, message: 'Transaksi berhasil disimpan.' };
}

function getTransactions(filters, token) {
  requireAdmin(token);
  const sheet = getSheet_();
  const lastRow = sheet.getLastRow();

  if (lastRow < 2) return [];

  filters = filters || {};
  const values = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
  const month = filters.month ? Number(filters.month) : null;
  const year = filters.year ? Number(filters.year) : null;
  const search = String(filters.search || '').trim().toLowerCase();
  const fromDate = String(filters.fromDate || '').trim();
  const toDate = String(filters.toDate || '').trim();
  const tz = Session.getScriptTimeZone();

  return values
    .filter(row => row[1] instanceof Date)
    .filter(row => {
      const d = row[1];
      const dateKey = Utilities.formatDate(d, tz, 'yyyy-MM-dd');
      const customer = String(row[2] || '').toLowerCase();

      if (month && d.getMonth() + 1 !== month) return false;
      if (year && d.getFullYear() !== year) return false;
      if (fromDate && dateKey < fromDate) return false;
      if (toDate && dateKey > toDate) return false;
      if (search && !customer.includes(search)) return false;
      return true;
    })
    .map(row => ({
      id: row[0],
      tanggal: Utilities.formatDate(row[1], tz, 'yyyy-MM-dd'),
      nama: row[2],
      nominal: Number(row[3]) || 0
    }))
    .reverse();
}

function getTransactionById(id, token) {
  requireAdmin(token);
  if (!id) throw new Error('ID transaksi tidak ditemukan.');

  const sheet = getSheet_();
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return null;

  const values = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
  const tz = Session.getScriptTimeZone();

  for (let i = 0; i < values.length; i++) {
    if (String(values[i][0]) === String(id)) {
      return {
        id: values[i][0],
        tanggal: values[i][1] instanceof Date
          ? Utilities.formatDate(values[i][1], tz, 'yyyy-MM-dd')
          : '',
        nama: String(values[i][2] || ''),
        nominal: Number(values[i][3]) || 0
      };
    }
  }

  throw new Error('Transaksi tidak ditemukan.');
}

function updateTransaction(data, token) {
  requireAdmin(token);
  if (!data || !data.id || !data.tanggal || !data.nama || data.nominal === undefined) {
    throw new Error('Data transaksi tidak lengkap.');
  }

  const nominal = Number(data.nominal);
  if (!Number.isFinite(nominal) || nominal <= 0) {
    throw new Error('Nominal harus berupa angka lebih dari 0.');
  }

  const tanggal = new Date(String(data.tanggal) + 'T00:00:00');
  if (isNaN(tanggal.getTime())) throw new Error('Tanggal tidak valid.');

  const nama = formatNamaPelanggan_(data.nama);
  if (!nama) throw new Error('Nama pelanggan wajib diisi.');

  const sheet = getSheet_();
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) throw new Error('Belum ada transaksi.');

  const ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === String(data.id)) {
      const rowNumber = i + 2;
      sheet.getRange(rowNumber, 2, 1, 3).setValues([[tanggal, nama, nominal]]);
      return { success: true, message: 'Transaksi berhasil diperbarui.' };
    }
  }

  throw new Error('Transaksi tidak ditemukan.');
}

function getDashboard(token) {
  requireAdmin(token);
  const now = new Date();
  const tz = Session.getScriptTimeZone();
  const todayKey = Utilities.formatDate(now, tz, 'yyyy-MM-dd');
  const month = now.getMonth(), year = now.getFullYear();
  const sales = getCashflowStats_(SHEET_NAME, todayKey, month, year);
  const other = getCashflowStats_(OTHER_INCOME_SHEET_NAME, todayKey, month, year);
  const expense = getCashflowStats_(EXPENSE_SHEET_NAME, todayKey, month, year);
  return {
    todayTotal: sales.todayTotal,
    monthTotal: sales.monthTotal,
    todayCount: sales.todayCount,
    monthCount: sales.monthCount,
    todayOtherIncome: other.todayTotal,
    monthOtherIncome: other.monthTotal,
    todayExpense: expense.todayTotal,
    monthExpense: expense.monthTotal,
    todayNet: sales.todayTotal + other.todayTotal - expense.todayTotal,
    monthNet: sales.monthTotal + other.monthTotal - expense.monthTotal
  };
}

function getCashflowStats_(sheetName, todayKey, month, year) {
  const sheet = sheetName === SHEET_NAME ? getSheet_() : getCashflowSheet_(sheetName);
  const lastRow = sheet.getLastRow();
  let todayTotal=0, monthTotal=0, todayCount=0, monthCount=0;
  if (lastRow < 2) return {todayTotal,monthTotal,todayCount,monthCount};
  const values = sheet.getRange(2, 2, lastRow - 1, 3).getValues();
  const tz = Session.getScriptTimeZone();
  values.forEach(row => {
    const date=row[0], nominal=Number(row[2])||0;
    if (!(date instanceof Date)) return;
    if (Utilities.formatDate(date,tz,'yyyy-MM-dd')===todayKey){todayTotal+=nominal;todayCount++;}
    if (date.getMonth()===month && date.getFullYear()===year){monthTotal+=nominal;monthCount++;}
  });
  return {todayTotal,monthTotal,todayCount,monthCount};
}

function getMonthlyRecap(year, token) {
  requireAdmin(token);
  const selectedYear = Number(year) || new Date().getFullYear();
  const sheet = getSheet_();
  const lastRow = sheet.getLastRow();

  const months = Array.from({ length: 12 }, (_, i) => ({
    month: i + 1,
    label: getMonthName_(i),
    total: 0,
    count: 0
  }));

  if (lastRow < 2) return months;

  const values = sheet.getRange(2, 2, lastRow - 1, 3).getValues();

  values.forEach(row => {
    const date = row[0];
    const nominal = Number(row[2]) || 0;

    if (!(date instanceof Date) || date.getFullYear() !== selectedYear) return;

    const index = date.getMonth();
    months[index].total += nominal;
    months[index].count++;
  });

  return months;
}

function getAvailableYears(token) {
  requireAdmin(token);
  const years = new Set([new Date().getFullYear()]);
  [SHEET_NAME, OTHER_INCOME_SHEET_NAME, EXPENSE_SHEET_NAME].forEach(name => {
    const sheet = name === SHEET_NAME ? getSheet_() : getCashflowSheet_(name);
    const lastRow = sheet.getLastRow();
    if (lastRow >= 2) sheet.getRange(2,2,lastRow-1,1).getValues().forEach(row=>{ if(row[0] instanceof Date) years.add(row[0].getFullYear()); });
  });
  return Array.from(years).sort((a,b)=>b-a);
}

function deleteTransaction(id, token) {
  requireAdmin(token);
  if (!id) throw new Error('ID transaksi tidak ditemukan.');

  const sheet = getSheet_();
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) throw new Error('Belum ada transaksi.');

  const ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();

  for (let i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === String(id)) {
      sheet.deleteRow(i + 2);
      return { success: true, message: 'Transaksi dihapus.' };
    }
  }

  throw new Error('Transaksi tidak ditemukan.');
}

function getMonthName_(index) {
  const names = [
    'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
    'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'
  ];
  return names[index];
}


function addOtherIncome(data, token) { return addCashflowEntry_(OTHER_INCOME_SHEET_NAME, data, token, 'Pemasukan lain berhasil disimpan.'); }
function addExpense(data, token) { return addCashflowEntry_(EXPENSE_SHEET_NAME, data, token, 'Pengeluaran berhasil disimpan.'); }

function addCashflowEntry_(sheetName, data, token, successMessage) {
  requireAdmin(token);
  if (!data || !data.tanggal || !data.keterangan || data.nominal === undefined) throw new Error('Tanggal, keterangan, dan nominal wajib diisi.');
  const nominal=Number(data.nominal); if(!Number.isFinite(nominal)||nominal<=0) throw new Error('Nominal harus berupa angka lebih dari 0.');
  const tanggal=new Date(String(data.tanggal)+'T00:00:00'); if(isNaN(tanggal.getTime())) throw new Error('Tanggal tidak valid.');
  const sheet=getCashflowSheet_(sheetName); sheet.appendRow([Utilities.getUuid(),tanggal,String(data.keterangan).trim(),nominal,new Date()]);
  return {success:true,message:successMessage};
}

function getCashflowEntries_(sheetName, filters, token) {
  requireAdmin(token); filters=filters||{}; const sheet=getCashflowSheet_(sheetName); const lastRow=sheet.getLastRow(); if(lastRow<2)return[];
  const values=sheet.getRange(2,1,lastRow-1,5).getValues(); const month=filters.month?Number(filters.month):null, year=filters.year?Number(filters.year):null, from=String(filters.fromDate||''), to=String(filters.toDate||''), search=String(filters.search||'').toLowerCase(); const tz=Session.getScriptTimeZone();
  return values.filter(r=>r[1] instanceof Date).filter(r=>{const d=r[1],key=Utilities.formatDate(d,tz,'yyyy-MM-dd'),desc=String(r[2]||'').toLowerCase();if(month&&d.getMonth()+1!==month)return false;if(year&&d.getFullYear()!==year)return false;if(from&&key<from)return false;if(to&&key>to)return false;if(search&&!desc.includes(search))return false;return true;}).map(r=>({id:r[0],tanggal:Utilities.formatDate(r[1],tz,'yyyy-MM-dd'),keterangan:r[2],nominal:Number(r[3])||0})).reverse();
}
function getOtherIncomes(filters, token){return getCashflowEntries_(OTHER_INCOME_SHEET_NAME,filters,token);}
function getExpenses(filters, token){return getCashflowEntries_(EXPENSE_SHEET_NAME,filters,token);}
function deleteOtherIncome(id, token){return deleteCashflowEntry_(OTHER_INCOME_SHEET_NAME,id,token,'Pemasukan lain dihapus.');}
function deleteExpense(id, token){return deleteCashflowEntry_(EXPENSE_SHEET_NAME,id,token,'Pengeluaran dihapus.');}
function deleteCashflowEntry_(sheetName,id,token,msg){requireAdmin(token);if(!id)throw new Error('ID tidak ditemukan.');const sheet=getCashflowSheet_(sheetName),last=sheet.getLastRow();if(last<2)throw new Error('Belum ada data.');const ids=sheet.getRange(2,1,last-1,1).getValues();for(let i=0;i<ids.length;i++){if(String(ids[i][0])===String(id)){sheet.deleteRow(i+2);return{success:true,message:msg};}}throw new Error('Data tidak ditemukan.');}

function getMonthlyCashflow(year, token){
  requireAdmin(token); const selectedYear=Number(year)||new Date().getFullYear(); const names=['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
  const rows=names.map((label,i)=>({month:i+1,label,pijat:0,otherIncome:0,expense:0,net:0}));
  function add(sheetName,key){const sheet=sheetName===SHEET_NAME?getSheet_():getCashflowSheet_(sheetName),last=sheet.getLastRow();if(last<2)return;sheet.getRange(2,2,last-1,3).getValues().forEach(r=>{if(r[0] instanceof Date&&r[0].getFullYear()===selectedYear){rows[r[0].getMonth()][key]+=Number(r[2])||0;}});}
  add(SHEET_NAME,'pijat');add(OTHER_INCOME_SHEET_NAME,'otherIncome');add(EXPENSE_SHEET_NAME,'expense');rows.forEach(r=>r.net=r.pijat+r.otherIncome-r.expense);return rows;
}
