const express = require('express');
const path = require('path');
const multer = require('multer');
const mysql = require('mysql2/promise');
const ffmpeg = require('fluent-ffmpeg');
const ffmpegInstaller = require('@ffmpeg-installer/ffmpeg');

ffmpeg.setFfmpegPath(ffmpegInstaller.path);
const app = express();
const PORT = 8080;

const storage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, 'uploads/'),
    filename: (req, file, cb) => {
        const decodedName = Buffer.from(file.originalname, 'latin1').toString('utf8');
        cb(null, Date.now() + '-' + decodedName);
    }
});
const upload = multer({ storage: storage });

app.use('/uploads', express.static('uploads'));
app.use(express.static('public'));
app.use(express.json());

let pool;
async function initDB() {
    pool = mysql.createPool({
        host: 'localhost',
        user: 'root',
        password: '741206', // 🚨 비밀번호 고정
        database: 'mytube',
        waitForConnections: true,
        connectionLimit: 10
    });
}
initDB();

// 🌟 업로드 멈춤 현상 해결을 위한 에러 핸들링 추가
app.post('/upload', upload.single('video'), async (req, res) => {
    try {
        const { channelId, title, description } = req.body;
        const filename = req.file.filename;
        const thumb = 'thumb-' + filename + '.png';

        ffmpeg(req.file.path)
            .screenshots({ timestamps: ['1'], filename: thumb, folder: 'uploads/', size: '320x180' })
            .on('end', async () => {
                await pool.query('INSERT INTO videos (channel_id, title, description, videoUrl, thumbnailUrl) VALUES (?, ?, ?, ?, ?)',
                    [channelId, title, description, '/uploads/' + filename, '/uploads/' + thumb]);
                res.json({ success: true });
            })
            .on('error', (err) => {
                console.error("FFmpeg 에러:", err);
                res.status(500).json({ message: '썸네일 생성 실패' });
            });
    } catch (e) {
        res.status(500).json({ message: '서버 에러' });
    }
});

app.get('/api/videos', async (req, res) => {
    const keyword = req.query.keyword || '';
    const [rows] = await pool.query(`SELECT v.*, c.name as channelName FROM videos v JOIN channels c ON v.channel_id = c.id WHERE v.title LIKE ? ORDER BY v.id DESC`, [`%${keyword}%`]);
    res.json(rows);
});

app.get('/api/videos/:id/comments', async (req, res) => {
    const [rows] = await pool.query(`SELECT c.*, ch.name as channelName FROM comments c JOIN channels ch ON c.channel_id = ch.id WHERE c.video_id = ? ORDER BY c.id DESC`, [req.params.id]);
    res.json(rows);
});

app.post('/api/videos/:id/comments', async (req, res) => {
    const { channelId, text } = req.body;
    await pool.query('INSERT INTO comments (video_id, channel_id, text) VALUES (?, ?, ?)', [req.params.id, channelId, text]);
    res.json({ success: true });
});

app.post('/api/videos/:id/view', async (req, res) => { await pool.query('UPDATE videos SET views = views + 1 WHERE id = ?', [req.params.id]); res.json({ success: true }); });
app.get('/api/videos/:id/details', async (req, res) => {
    const [vid] = await pool.query('SELECT v.*, c.name as channelName FROM videos v JOIN channels c ON v.channel_id = c.id WHERE v.id = ?', [req.params.id]);
    res.json({ video: vid[0] });
});

app.get('/api/channels/:id/videos', async (req, res) => {
    const [rows] = await pool.query('SELECT * FROM videos WHERE channel_id = ? ORDER BY id DESC', [req.params.id]);
    res.json(rows);
});

app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'index.html')));
app.listen(PORT, () => console.log(`🚀 서버 구동 중: http://localhost:${PORT}`));