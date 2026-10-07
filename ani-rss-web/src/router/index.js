import {createRouter, createWebHashHistory} from 'vue-router'
import AddSubscriptionView from '@/view/home/AddSubscriptionView.vue'
import SubscriptionView from '@/view/home/SubscriptionView.vue'
import TorrentsInfosView from '@/view/home/TorrentsInfosView.vue'
import LogsView from '@/view/home/LogsView.vue'
import ConfigView from '@/view/home/ConfigView.vue'
import {startupPage} from '@/js/global.js'

const startupPaths = ['/subscriptions/add', '/subscriptions']

const routes = [
    {
        path: '/',
        redirect: () => startupPaths.includes(startupPage.value) ? startupPage.value : '/subscriptions/add'
    },
    {
        path: '/home',
        redirect: '/subscriptions/add'
    },
    {
        path: '/subscriptions/add',
        component: AddSubscriptionView
    },
    {
        path: '/subscriptions',
        component: SubscriptionView
    },
    {
        path: '/downloads',
        component: TorrentsInfosView
    },
    {
        path: '/logs',
        component: LogsView
    },
    {
        path: '/settings',
        component: ConfigView
    }
]

const router = createRouter({
    history: createWebHashHistory(),
    routes
})

export default router
