<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
const auth=useAuthStore()
const router=useRouter()
function expired() { auth.user=null; if(router.currentRoute.value.path!=='/login') router.replace('/login') }
onMounted(()=>window.addEventListener('session-expired',expired))
onUnmounted(()=>window.removeEventListener('session-expired',expired))
</script>
<template><router-view /></template>
