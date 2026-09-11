<!-- 登录页左侧品牌区：平台名称 + 宣传语 + 特性列表（FR-LOGIN-001） -->
<template>
  <div class="login-brand-view">
    <!-- Logo + 平台名称 -->
    <div class="logo relative z-[100] flex items-center">
      <ArtLogo class="icon" size="46" />
      <h1 class="ml-2.5 text-xl font-normal text-g-900">
        {{ AppConfig.systemInfo.name }}
      </h1>
    </div>

    <!-- 插画 -->
    <div class="left-img">
      <ThemeSvg :src="loginIcon" size="100%" />
    </div>

    <!-- 宣传语 + 特性列表 -->
    <div class="text-wrap">
      <h1 class="text-2xl font-normal text-g-900">
        {{ $t('login.brand.title') }}
      </h1>
      <p class="mt-2.5 text-sm text-g-600">{{ $t('login.brand.subTitle') }}</p>

      <ul class="mt-7 grid grid-cols-2 gap-3 gap-x-6">
        <li
          v-for="(feature, index) in features"
          :key="index"
          class="feature-item flex items-center p-3"
        >
          <ArtSvgIcon :icon="feature.icon" class="shrink-0 text-2xl feature-icon" />
          <div class="ml-2.5 flex flex-col">
            <span class="text-sm font-medium text-g-800">
              {{ $t(`login.brand.features[${index}].title`) }}
            </span>
            <span class="mt-0.5 text-xs text-g-600">
              {{ $t(`login.brand.features[${index}].desc`) }}
            </span>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
  import AppConfig from '@/config'
  import loginIcon from '@imgs/svg/login_icon.svg'

  defineOptions({ name: 'LoginBrandView' })

  // 特性列表（图标固定，文案走 i18n）
  const features = [
    { icon: 'ri:stack-line' },
    { icon: 'ri:calendar-check-line' },
    { icon: 'ri:shield-user-line' },
    { icon: 'ri:file-shield-2-line' }
  ]
</script>

<style lang="scss" scoped>
  // 颜色变量引用（与 LoginLeftView 保持一致的主题口径）
  $primary-light-8: var(--el-color-primary-light-8);
  $primary-light-9: var(--el-color-primary-light-9);
  $main-bg: var(--default-box-color);

  $bg-mix-light-9: color-mix(in srgb, $primary-light-9 100%, $main-bg);
  $bg-mix-light-8: color-mix(in srgb, $primary-light-8 80%, $main-bg);

  .login-brand-view {
    position: relative;
    box-sizing: border-box;
    width: 65vw;
    height: 100%;
    padding: 15px;
    overflow: hidden;
    background-color: $bg-mix-light-9;

    .left-img {
      position: absolute;
      inset: 0 0 32%;
      z-index: 10;
      width: 40%;
      margin: auto;
      animation: slideInLeft 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
    }

    .text-wrap {
      position: absolute;
      bottom: 60px;
      left: 0;
      width: 100%;
      padding: 0 6vw;
      text-align: left;
      animation: slideInLeft 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
    }

    .feature-item {
      background-color: $bg-mix-light-8;
      border: 1px solid var(--art-card-border);
      border-radius: var(--custom-radius);
      transition: transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);

      &:hover {
        transform: translateY(-2px);
      }

      .feature-icon {
        color: var(--art-primary);
      }
    }

    @media only screen and (width <= 1600px) {
      width: 60vw;

      .text-wrap {
        bottom: 40px;
      }
    }

    @media only screen and (width <= 1180px) {
      width: auto;
      height: auto;
      padding: 0;
      background: transparent;

      .logo,
      .left-img,
      .text-wrap {
        display: none;
      }
    }
  }

  // 入场动画（沿用 LoginLeftView 既有左滑入场曲线，不新增动画效果）
  @keyframes slideInLeft {
    from {
      opacity: 0;
      transform: translateX(-30px);
    }

    to {
      opacity: 1;
      transform: translateX(0);
    }
  }

  // 暗色主题
  .dark .login-brand-view {
    background-color: color-mix(in srgb, $primary-light-9 60%, #070707);

    @media only screen and (width <= 1180px) {
      background: transparent;
    }

    .feature-item {
      background-color: color-mix(in srgb, $primary-light-9 60%, #070707);
    }
  }
</style>
