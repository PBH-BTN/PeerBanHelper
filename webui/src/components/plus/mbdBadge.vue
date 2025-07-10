<template>
  <div>
    <div class="mbd-badge">
      <div class="mbd-logo">
        <img src="https://mbd.pub/o/_nuxt/img/4ed3725.svg" alt="面包多Logo" />
      </div>
      <div class="mbd-text">
        <div class="mbd-title">为我发电</div>
        <div class="mbd-subtitle">在面包多赞助我</div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.mbd-badge {
  width: 380px;
  height: 138px;
  background: linear-gradient(90deg, #ffe6b3 0%, #ffd591 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(255, 176, 0, 0.08);
  font-family: 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}
.mbd-logo {
  width: 80px;
  height: 80px;
  margin-left: 18px;
  margin-right: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mbd-logo img {
  width: 80px;
  height: 80px;
  display: block;
}
.mbd-text {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.mbd-title {
  font-size: 32px;
  font-weight: bold;
  color: #c97a1d;
  margin-bottom: 8px;
}
.mbd-subtitle {
  font-size: 20px;
  color: #c97a1d;
  opacity: 0.65;
}
</style>
