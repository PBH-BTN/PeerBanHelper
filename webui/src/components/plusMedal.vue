<template>
  <div class="shape_box">
    <div class="shape_black"><span></span><span></span><span></span></div>
    <div class="shape_out">
      <div class="shape">
        <div class="shape_in">
          <div class="shape_backend"></div>
          <div class="top_star"><span>★</span><span>★</span><span>★</span></div>
          <div class="shape_text_backend_A"></div>
          <div class="shape_text_backend_B"></div>
          <div class="shape_text">{{ text }}</div>
          <div class="under_star"><span>★</span><span>★</span><span>★</span></div>
        </div>
      </div>
    </div>
    <div class="scroll_star">
      <span class="sc_star_x"></span>
      <span class="sc_star_y"></span>
      <span class="sc_star_o"></span>
    </div>
    <div class="scroll_star2">
      <span class="sc_star_x"></span>
      <span class="sc_star_y"></span>
      <span class="sc_star_o"></span>
    </div>
    <div class="scroll_star3">
      <span class="sc_star_x"></span>
      <span class="sc_star_y"></span>
      <span class="sc_star_o"></span>
    </div>
  </div>
</template>
<script lang="ts" setup>
const { text } = defineProps<{
  text: string
}>()
</script>
<style scoped>
.shape_box {
  position: relative;
  width: 140px;
  height: 140px;
}

.shape_out {
  position: relative;
  height: 140px;
  width: 140px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    #c7aa68,
    #ce9f4f,
    #d0b15e,
    #fff6c5,
    #d0b15e,
    #ce9f4f,
    #c7aa68
  );
  background-size: 100%;
  background-position: 50% 50%;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.2);
}

.shape {
  position: absolute;
  height: 130px;
  width: 130px;
  top: 5px;
  left: 5px;
  border-radius: 50%;
  background: linear-gradient(45deg, #c7aa68, #ce9f4f, #d0b15e, #fff6c5, #d0b15e, #ce9f4f, #c7aa68);
  background-size: 150%;
  background-position: 50% 50%;
}

.shape_in {
  position: absolute;
  width: 126px;
  height: 126px;
  top: 2px;
  left: 2px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    #c7aa68,
    #ce9f4f,
    #d0b15e,
    #fff6c5,
    #d0b15e,
    #ce9f4f,
    #c7aa68
  );
  background-size: 300%;
  background-position: 50% 50%;
  box-shadow: inset 0 0 0 1px rgba(206, 159, 72, 1);
}

.shape_backend::before,
.shape_backend::after {
  position: absolute;
  content: '';
  width: 91px;
  height: 91px;
  top: 17px;
  left: 17px;
  border-radius: 50%;
  background-size: 300%;
  background: linear-gradient(45deg, #c7aa68, #ce9f4f, #d0b15e, #fff6c5, #d0b15e, #ce9f4f, #c7aa68);
  box-shadow: none;
}

.shape_backend::after {
  width: 87px;
  height: 87px;
  top: 19px;
  left: 19px;
  box-shadow: inset 0 0 0 1px rgba(206, 159, 72, 1);
  background: linear-gradient(
    135deg,
    #c7aa68,
    #ce9f4f,
    #d0b15e,
    #fff6c5,
    #d0b15e,
    #ce9f4f,
    #c7aa68
  );
}

.shape_text_backend_A,
.shape_text_backend_B,
.shape_text {
  position: absolute;
  width: 130px;
  height: 22px;
  top: 45px;
  left: -14px;
}
.shape_text {
  line-height: 22px;
  padding: 6px 12px;
  text-align: center;
  font-size: 14px;
  color: #fff;
  font-weight: 700;
  text-shadow: 1px 1px 1px rgba(0, 0, 0, 0.5);
  text-transform: uppercase;
  background: linear-gradient(45deg, #c10000, #e50000, #c10000);
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.2);
}

.shape_text_backend_A::before,
.shape_text_backend_A::after {
  content: '';
  position: absolute;
  background: linear-gradient(270deg, #740000, #e50000);
  height: 34px;
  width: 30px;
  top: 7px;
  left: -24px;
}

.shape_text_backend_A::after {
  left: 147px;
  background: linear-gradient(90deg, #740000, #e50000);
}

.shape_text_backend_B::before,
.shape_text_backend_B::after {
  content: '';
  position: absolute;
  border-width: 17px;
  border-style: solid;
  border-color: transparent transparent transparent var(--color-bg-3);
  top: 7px;
  left: -24px;
}

.shape_text_backend_B::after {
  left: 144px;
  border-color: transparent var(--color-bg-3) transparent transparent;
}

.top_star,
.under_star {
  position: absolute;
  width: 60px;
  top: 24px;
  left: 37px;
}

.under_star {
  top: 78px;
}

.under_star span,
.top_star span {
  position: relative;
  font-family: '黑体';
  color: #b98e26;
  font-size: 16px;
  float: left;
  display: inline-block;
  text-shadow: -1px -1px 0px rgba(255, 255, 255, 0.7);
}

.top_star span:nth-child(3),
.under_star span:nth-child(1) {
  transform: rotate(20deg);
}

.top_star span:nth-child(2),
.under_star span:nth-child(2) {
  font-size: 20px;
  margin-top: 6px;
}

.top_star span:nth-child(2) {
  margin-top: -6px;
}

.top_star span:nth-child(1),
.under_star span:nth-child(3) {
  transform: rotate(-20deg);
}

.shape_black {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;

  span {
    position: absolute;
    display: inline-block;
    width: 120px;
    height: 120px;
    border-radius: 5px;
    background: linear-gradient(
      45deg,
      #c7aa68,
      #ce9f4f,
      #d0b15e,
      #fff6c5,
      #d0b15e,
      #ce9f4f,
      #c7aa68
    );
    box-shadow: 0px 0px 5px rgba(0, 0, 0, 0.5);

    &:nth-child(1) {
      transform: rotateZ(30deg);
    }

    &:nth-child(2) {
      transform: rotateZ(60deg);
    }

    &:nth-child(3) {
      transform: rotateZ(90deg);
    }
  }
}

.scroll_star {
  position: absolute;
  width: 13px;
  height: 13px;
  top: 17px;
  left: 110px;
  animation: zoomInOut 2s infinite;
}
.scroll_star2 {
  position: absolute;
  width: 13px;
  height: 13px;
  top: 45px;
  left: 27px;
  transform: scale(2);
  animation: zoomInOut 2s infinite;
}
.scroll_star3 {
  position: absolute;
  width: 13px;
  height: 13px;
  top: 95px;
  left: 93px;
  animation: zoomInOut 2s infinite;
  transform: scale(0.7);
}
.sc_star_x,
.sc_star_y {
  position: absolute;
  top: 5px;
  left: 0px;
  width: 13px; /* 椭圆的宽度 */
  height: 3px; /* 椭圆的高度 */
  background-image: radial-gradient(circle, #fff, #fff); /* 椭圆的填充颜色 */
  border-radius: 90%; /* 边框半径，创建圆形形状 */
}
.sc_star_y {
  top: 0px;
  left: 5px;
  width: 3px;
  height: 13px;
}
.sc_star_o {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 7px;
  height: 7px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0px 0px 5px rgba(255, 255, 180, 0.9);
}
/* 定义放大缩小的无限循环动画 */
@keyframes zoomInOut {
  0% {
    transform: scale(1.1);
    opacity: 0.9;
    transform: rotate(0deg);
  }
  50% {
    transform: scale(1.5);
    opacity: 0.1;
    transform: rotate(180deg);
  }
  100% {
    transform: scale(1.1);
    opacity: 0.9;
    transform: rotate(360deg);
  }
}
</style>
