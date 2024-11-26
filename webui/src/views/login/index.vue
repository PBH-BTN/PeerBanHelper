<template>
  <a-row justify="center">
    <a-col :xs="24" :sm="20" :md="16" :lg="12" :xl="8">
      <a-space direction="vertical" fill>
        <a-typography-title :heading="3">{{ t('login.form.title') }}</a-typography-title>
        <a-form
          ref="loginForm"
          :model="loginConfig"
          class="login-form"
          layout="vertical"
          @submit="handleSubmit"
        >
          <a-form-item
            field="token"
            :rules="
              [
                { required: true, message: t('login.form.password.errMsg') },
                {
                  validator: (value: string, callback) => {
                    if (/[a-zA-Z0-9-_]+/.test(value)) {
                      callback()
                    } else {
                      callback(t('login.form.password.errMsg'))
                    }
                  }
                }
              ] as FieldRule[]
            "
            :validate-trigger="['change', 'input']"
            hide-label
          >
            <a-input-password
              v-model="loginConfig.token"
              :placeholder="t('login.form.password.placeholder')"
              allow-clear
            >
              <template #prefix>
                <icon-lock />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item field="rememberPassword" class="login-form-password-actions">
            <a-checkbox checked="rememberPassword" :model-value="loginConfig.rememberPassword">
              {{ t('login.form.rememberPassword') }}
            </a-checkbox>
            <a-link
              :style="{ marginLeft: 'auto' }"
              href="https://docs.pbh-btn.com/docs/network/http-server/#%E6%9B%B4%E6%94%B9-webui-token"
            >
              {{ t('login.form.forgetPassword') }}
            </a-link>
          </a-form-item>
          <a-button type="primary" html-type="submit" long :loading="loading">
            {{ t('login.form.login') }}
          </a-button>
        </a-form>
      </a-space>
    </a-col>
  </a-row>
</template>

<script lang="ts" setup>
import { useEndpointStore } from '@/stores/endpoint'
import { Message, type FieldRule, type FormInstance } from '@arco-design/web-vue'
import { onMounted, ref, type UnwrapRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
const endpointStore = useEndpointStore()
const { t } = useI18n()
const loading = ref(false)
const loginConfig = ref({
  rememberPassword: true,
  token: endpointStore.authToken
})

const loginForm = ref<FormInstance>()
const handleSubmit: FormInstance['onSubmit'] = async ({ errors, values }) => {
  const errorFields = errors ? Object.keys(errors) : []
  if (errorFields.length > 0) {
    loginForm.value?.scrollToField(errorFields[0])
    return
  }
  const { token, rememberPassword } = values as UnwrapRef<typeof loginConfig>
  if (loading.value) return
  loading.value = true
  loginForm.value?.setFields({
    token: { status: 'validating', message: '' }
  })
  try {
    await endpointStore.setAuthToken(token, rememberPassword)
    Message.success({ content: t('login.form.login.success'), resetOnHover: true })
  } catch (err) {
    loginForm.value?.setFields({
      token: {
        status: 'error',
        message: `${t('login.form.login.failed')}  ${(err as Error).message}`
      }
    })
  } finally {
    loading.value = false
  }
}

const { query } = useRoute()
onMounted(() => {
  if (query.token) {
    loginConfig.value.token = query.token as string
    loginForm.value?.$emit(
      'submit',
      { values: loginConfig.value, errors: undefined },
      new Event('submit')
    )
  }
})
</script>
