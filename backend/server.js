const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Serve config.json as static
app.get('/config.json', (req, res) => {
  res.sendFile(path.join(__dirname, 'config.json'));
});

// Serve a mock MDBList JSON list for local testing
app.get('/mock-list.json', (req, res) => {
  res.json([
    {
      "title": "Sintel",
      "type": "movie",
      "year": 2010
    },
    {
      "title": "Big Buck Bunny",
      "type": "movie",
      "year": 2008
    }
  ]);
});

// Setup SQLite database
const dbPath = path.join(__dirname, 'sync.db');
const db = new sqlite3.Database(dbPath, (err) => {
  if (err) {
    console.error('Failed to connect to database:', err.message);
  } else {
    console.log('Connected to SQLite database at:', dbPath);
    initDatabase();
  }
});

function initDatabase() {
  db.serialize(() => {
    // PlaybackHistory table
    db.run(`
      CREATE TABLE IF NOT EXISTS playback_history (
        userId TEXT,
        streamId TEXT,
        lastPosition INTEGER,
        totalDuration INTEGER,
        isDismissed INTEGER DEFAULT 0,
        updatedAt INTEGER,
        PRIMARY KEY (userId, streamId)
      )
    `);

    // WatchedStates table
    db.run(`
      CREATE TABLE IF NOT EXISTS watched_states (
        userId TEXT,
        itemId TEXT,
        status INTEGER DEFAULT 0,
        PRIMARY KEY (userId, itemId)
      )
    `);
  });
}

// Sync Playback History (POST)
app.post('/api/sync/playback_history', (req, res) => {
  const { userId, streamId, lastPosition, totalDuration, isDismissed, updatedAt } = req.body;
  if (!userId || !streamId) {
    return res.status(400).json({ error: 'userId and streamId are required' });
  }

  const sql = `
    INSERT INTO playback_history (userId, streamId, lastPosition, totalDuration, isDismissed, updatedAt)
    VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT(userId, streamId) DO UPDATE SET
      lastPosition = excluded.lastPosition,
      totalDuration = excluded.totalDuration,
      isDismissed = excluded.isDismissed,
      updatedAt = excluded.updatedAt
  `;

  db.run(sql, [userId, streamId, lastPosition, totalDuration, isDismissed ? 1 : 0, updatedAt || Date.now()], function(err) {
    if (err) {
      console.error(err.message);
      return res.status(500).json({ error: err.message });
    }
    res.json({ success: true, message: 'Playback history synchronized' });
  });
});

// Get Playback History (GET)
app.get('/api/sync/playback_history', (req, res) => {
  const { userId } = req.query;
  if (!userId) {
    return res.status(400).json({ error: 'userId query parameter is required' });
  }

  const sql = `SELECT * FROM playback_history WHERE userId = ?`;
  db.all(sql, [userId], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    const formattedRows = rows.map(r => ({
      userId: r.userId,
      streamId: r.streamId,
      lastPosition: r.lastPosition,
      totalDuration: r.totalDuration,
      isDismissed: r.isDismissed === 1,
      updatedAt: r.updatedAt
    }));
    res.json(formattedRows);
  });
});

// Sync Watched States (POST)
app.post('/api/sync/watched_states', (req, res) => {
  const { userId, itemId, status } = req.body;
  if (!userId || !itemId) {
    return res.status(400).json({ error: 'userId and itemId are required' });
  }

  const sql = `
    INSERT INTO watched_states (userId, itemId, status)
    VALUES (?, ?, ?)
    ON CONFLICT(userId, itemId) DO UPDATE SET
      status = excluded.status
  `;

  db.run(sql, [userId, itemId, status ? 1 : 0], function(err) {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    res.json({ success: true, message: 'Watched states synchronized' });
  });
});

// Get Watched States (GET)
app.get('/api/sync/watched_states', (req, res) => {
  const { userId } = req.query;
  if (!userId) {
    return res.status(400).json({ error: 'userId query parameter is required' });
  }

  const sql = `SELECT * FROM watched_states WHERE userId = ?`;
  db.all(sql, [userId], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: err.message });
    }
    const formattedRows = rows.map(r => ({
      userId: r.userId,
      itemId: r.itemId,
      status: r.status === 1
    }));
    res.json(formattedRows);
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Sync Server running on port ${PORT}`);
});
