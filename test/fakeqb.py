from flask import Flask,jsonify
app = Flask(__name__)

T={
    "added_on": 1754149278,
    "amount_left": 0,
    "auto_tmm": False,
    "availability": 1,
    "category": "iso",
    "comment": "",
    "completed": 273454316,
    "completion_on": 1754149352,
    "content_path": "/mnt/data/Downloads/test.iso",
    "dl_limit": 0,
    "dlspeed": 0,
    "download_path": "/root/Downloads/temp",
    "downloaded": 277394161,
    "downloaded_session": 0,
    "eta": 8640000,
    "f_l_piece_prio": False,
    "force_start": False,
    "has_metadata": True,
    "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "inactive_seeding_time_limit": -2,
    "infohash_v1": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "infohash_v2": "",
    "last_activity": 1754150006,
    "magnet_uri": "",
    "max_inactive_seeding_time": -1,
    "max_ratio": 20,
    "max_seeding_time": -1,
    "name": "iso",
    "num_complete": 0,
    "num_incomplete": 873,
    "num_leechs": 0,
    "num_seeds": 0,
    "popularity": 74335.9843075997,
    "priority": 0,
    "private": False,
    "progress": 1,
    "ratio": 20.126362328153,
    "ratio_limit": -2,
    "reannounce": 0,
    "root_path": "",
    "save_path": "/mnt/data/Downloads/",
    "seeding_time": 654,
    "seeding_time_limit": -2,
    "seen_complete": 1754150006,
    "seq_dl": False,
    "size": 273454316,
    "state": "stoppedUP",
    "super_seeding": False,
    "tags": "iso",
    "time_active": 712,
    "total_size": 273454316,
    "tracker": "http://127.0.0.1:7777/announce",
    "trackers_count": 72,
    "up_limit": 0,
    "uploaded": 5582935392,
    "uploaded_session": 0,
    "upspeed": 0
  }
T100=[T]*100

P={
  "addition_date": 1732795527,
  "availability": 1,
  "comment": "Ubuntu",
  "completion_date": 1732795770,
  "created_by": "mktorrent 1.1",
  "creation_date": 1726165521,
  "dl_limit": -1,
  "dl_speed": 0,
  "dl_speed_avg": 28044864,
  "download_path": "/root/Downloads/temp",
  "eta": 8640000,
  "has_metadata": True,
  "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "infohash_v1": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "infohash_v2": "",
  "is_private": False,
  "last_seen": 1732795770,
  "name": "iso",
  "nb_connections": 0,
  "nb_connections_limit": -1,
  "peers": 0,
  "peers_total": 27,
  "piece_size": 262144,
  "pieces_have": 25991,
  "pieces_num": 25991,
  "popularity": 0.00721623295802137,
  "private": False,
  "progress": 1,
  "reannounce": 0,
  "save_path": "/root/Downloads",
  "seeding_time": 715055,
  "seeds": 0,
  "seeds_total": 0,
  "share_ratio": 0.00196283481461965,
  "time_elapsed": 715298,
  "total_downloaded": 6814902049,
  "total_downloaded_session": 0,
  "total_size": 6813349888,
  "total_uploaded": 13376527,
  "total_uploaded_session": 0,
  "total_wasted": 0,
  "up_limit": -1,
  "up_speed": 0,
  "up_speed_avg": 18
}

@app.route("/api/v2/app/version")
def version():
    return "v5.1.1.10"

@app.route("/api/v2/app/buildInfo")
def buildInfo():
    return jsonify({"bitness":64,"boost":"1.86.0","libtorrent":"1.2.20.0","openssl":"3.5.1","platform":"linux","qt":"6.9.1","zlib":"1.3.1.zlib-ng"})

@app.route("/api/v2/app/setPreferences",methods=["POST"])
def setPreferences():
    return jsonify({})

@app.route("/api/v2/sync/maindata")
def maindata():
    return jsonify({"server_state":{"alltime_dl":1,"alltime_ul":1}})

@app.route("/api/v2/app/preferences")
def preferences():
    return jsonify({"dl_limit":1,"up_limit":1})

@app.route("/api/v2/torrents/info")
def info():
    return jsonify(T100)

@app.route("/api/v2/torrents/properties")
def properties():
    return jsonify(P)

@app.route("/api/v2/sync/torrentPeers")
def torrentPeers():
    return jsonify({"full_update":True,"peers":{},"rid":1,"show_flags":True})

app.run(port=8080,host="127.0.0.1")