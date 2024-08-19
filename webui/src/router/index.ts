import {
  createRouter,
  createWebHistory,
  useRoute,
  useRouter,
  type RouteRecordRaw
} from 'vue-router'
import Dashboard from '../views/dashboard/index.vue'
import { computed, h } from 'vue'
import BanList from '@/views/banlist/index.vue'
import BanLog from '@/views/banlog/index.vue'
import Ranks from '@/views/ranks/index.vue'
import RuleMetric from '@/views/rule-metrics/index.vue'
import GenericBlackList from '@/views/rule-management/components/generic/index.vue'
import SubscribeManagement from '@/views/rule-management/components/subscribe/index.vue'
import { genIconComponent } from '@/components/iconFont'
import { IconCloud, IconLocation, IconStorage } from '@arco-design/web-vue/es/icon'
export const routerOptions: RouteRecordRaw[] = [
  {
    path: '/dashboard',
    name: 'dashboard',
    meta: {
      label: 'router.dashboard',
      needLogin: true
    },
    component: Dashboard
  },
  {
    path: '/list',
    name: 'banlist',
    meta: {
      label: 'router.banlist',
      needLogin: true
    },
    component: BanList
  },
  {
    path: '/log',
    name: 'banlogs',
    meta: {
      label: 'router.banlogs',
      needLogin: true
    },
    component: BanLog
  },
  {
    path: '/rule',
    name: 'rule_management',
    meta: {
      label: 'router.rule_management',
      disableAutoUpdate: true,
      needLogin: true
    },
    children: [
      {
        path: '/ruleSubscribe',
        name: 'rule_management_subscribe',
        meta: {
          label: 'page.rule_management.ruleSubscribe.title',
          icon: () => h(IconCloud),
          needLogin: true
        },
        component: SubscribeManagement
      },
      {
        path: '/ruleIp',
        name: 'rule_management_ip',
        meta: {
          label: 'page.rule_management.ip',
          icon: genIconComponent('icon-IP'),
          needLogin: true
        },
        component: GenericBlackList,
        props: { type: 'ip' }
      },
      {
        path: '/rulePort',
        name: 'rule_management_port',
        meta: {
          label: 'page.rule_management.port',
          icon: genIconComponent('icon-dituleiduankou'),
          needLogin: true
        },
        component: GenericBlackList,
        props: { type: 'port' }
      },
      {
        path: '/ruleAsn',
        name: 'rule_management_asn',
        meta: {
          label: 'page.rule_management.asn',
          icon: () => h(IconStorage),
          needLogin: true
        },
        component: GenericBlackList,
        props: { type: 'asn' }
      },
      {
        path: '/ruleRegion',
        name: 'rule_management_region',
        meta: {
          label: 'page.rule_management.region',
          icon: () => h(IconLocation),
          needLogin: true
        },
        component: GenericBlackList,
        props: { type: 'region' }
      },
      {
        path: '/ruleCity',
        name: 'rule_management_city',
        meta: {
          label: 'page.rule_management.city',
          icon: genIconComponent('icon-city'),
          needLogin: true
        },
        component: GenericBlackList,
        props: { type: 'city' }
      }
      // {
      //   path: '/ruleNetType',
      //   name: 'rule_management_netType',
      //   meta: {
      //     label: 'page.rule_management.netType',
      //     icon: genIconComponent('icon-kuandai'),
      //     needLogin: true
      //   },
      //   component: GenericBlackList, props: { type: 'netType' }
      // }
    ]
  },
  {
    path: '/metrics',
    name: 'metrics',
    meta: {
      label: 'router.metrics',
      needLogin: true
    },
    children: [
      {
        path: '/metricsRule',
        name: 'rule_metrics',
        meta: {
          label: 'router.metrics.ruleMetrics',
          needLogin: true
        },
        component: RuleMetric
      },
      {
        path: '/metricsCharts',
        name: 'charts',
        meta: {
          label: 'router.metrics.charts',
          needLogin: true
        },
        component: () => import('@/views/charts/index.vue')
      },
      {
        path: '/metricsRank',
        name: 'rank',
        meta: {
          label: 'router.rank',
          needLogin: true
        },
        component: Ranks
      }
    ]
  },
  {
    path: '/init',
    name: 'init',
    meta: {
      hide: true,
      disableAutoUpdate: true
    },
    component: () => import('@/views/oobe/index.vue')
  },
  {
    path: '/login',
    name: 'login',
    meta: {
      hide: true,
      disableAutoUpdate: true
    },
    component: () => import('@/views/login/index.vue')
  }
]

export let basePath = location.pathname
const flatedRouter = routerOptions.flatMap((item) => (item.children ? item.children : [item]))
for (const item of flatedRouter) {
  if (basePath.endsWith(item.path)) {
    basePath = basePath.slice(0, -item.path.length)
    break
  }
}

routerOptions.push(
  ...[
    // default home page
    {
      path: '/',
      redirect: '/dashboard',
      meta: {
        hide: true
      }
    }
  ]
)

const router = createRouter({
  history: createWebHistory(basePath),
  routes: routerOptions
})
router.afterEach((to, from) => {
  if (!from.name) {
    to.meta.transition = 'normal'
  } else {
    const toDepth = flatedRouter.findIndex((item) => item.name === to.name)
    const fromDepth = flatedRouter.findIndex((item) => item.name === from.name)
    to.meta.transition = toDepth > fromDepth ? 'route-right' : 'route-left'
  }
})

export default router

export function useViewRoute() {
  const router = useRouter()
  const route = useRoute()
  const goto = (name: string | number) => {
    router.replace({ name: `${name}` })
  }
  const routers = computed(() => {
    return routerOptions.filter((item) => !item.meta?.hidden)
  })
  const currentName = computed(() => route.name as string | undefined)
  return [routers, currentName, goto] as const
}
