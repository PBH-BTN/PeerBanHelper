function updateViews() {
    safeRun(()=>updateBanCounterView(document.getElementById("view-bancounter")));
    safeRun(()=>updateBanListView(document.getElementById("view-banlist")));
    safeRun(()=>updateClientStatus(document.getElementById("view-clientstatus")));
}

function safeRun(fun){
    try{
        fun();
    }catch (e){
       console.log(e);
    }
}

function updateClientStatus(node){
    fetch("/api/clientStatus")
        .then(response => response.json())
        .then(json => {
            const ol = document.createElement('ul');
            json.forEach(client => {document.getElementById("view-bancounter")
                const li = document.createElement('li');
                li.innerHTML = `<b>${client.name}</b> (${client.endpoint})`;
                // li.style.fontSize = '22px';
                const ul = document.createElement('ul');
                const status = document.createElement('li');
                if (client.status === "UNKNOWN") {
                    status.innerHTML = "<span style='color: darkgray'>？未知 - PeerBanHelper 可能还没有与此客户端通信</span>"
                }
                if (client.status === "ERROR") {
                    status.innerHTML = "<span style='color: red'>✘ 错误 - 与客户端通信时出错，请检查日志文件</span>"
                }
                if (client.status === "HEALTHY") {
                    status.innerHTML = "<span style='color: green'>✔ 正常 - 状态良好</span>"
                }
                // Torrents
                let torrentsCount = "未知";
                if(client.torrents !== undefined){
                    torrentsCount= `${client.torrents}`
                }
                const torrents = document.createElement("li");
                torrents.innerHTML = `活动种子数: ${torrentsCount}`;
                // Peers
                let peersCount = "未知";
                if(client.peers !== undefined){
                    peersCount= `${client.peers}`
                }
                const peers = document.createElement("li");
                peers.innerHTML = `已连接的对等体: ${peersCount}`;
                ul.append(...[status, torrents, peers]);
                li.appendChild(ul);
                ol.appendChild(li);
            });
            node.innerHTML = "";
            node.appendChild(ol);
        })
        .catch(err => {
            node.innerHTML = `<i>请求错误：${err}</i>`;
        });
}

function updateBanCounterView(node) {
    fetch("/api/statistic")
        .then(response => response.json())
        .then(json => {
            node.innerHTML = `共检查 ${json.checkCounter} 次，封禁 ${json.peerBanCounter} 次 Peers，解除已到期的封禁 ${json.peerUnbanCounter} 次。`;
        })
        .catch(err => {
            node.innerHTML = `<i>请求错误：${err}</i>`;
        });
}

function updateBanListView(node) {
    fetch("/api/banlist")
        .then(response => response.json())
        .then(json => {
            const ol = document.createElement('ul');
            let count = 0;
            json.forEach(ban => {
                count++;
                const li = document.createElement('li');
                li.innerHTML = `<b>${ban.address.ip + ":" + ban.address.port}</b>`;
                // li.style.fontSize = '22px';
                const ul = document.createElement('ul');
                const banAt = document.createElement('li');
                banAt.innerHTML = `封禁时间: ${stdTime(new Date(ban.banMetadata.banAt))}`;
                const unbanAt = document.createElement('li');
                unbanAt.innerHTML = `解封时间: ${stdTime(new Date(ban.banMetadata.unbanAt))}`;
                const description = document.createElement('li');
                description.innerHTML = `描述: ${ban.banMetadata.description}`;
                ul.append(...[banAt, unbanAt, description]);
                li.appendChild(ul);
                ol.appendChild(li);
            });
            node.innerHTML = "";
            const summary = document.createElement('p');
            summary.innerHTML = `当前共有 ${count} 个 Peer 处于封禁状态。`
            node.appendChild(summary);
            node.appendChild(ol);
        })
        .catch(err => {
            node.innerHTML = `<i>请求错误：${err}</i>`
        });
}

function stdTime(dateObj){
    return dateObj.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
}