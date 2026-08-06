import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePlanStore = defineStore('plan', () => {
    const currentPlan = ref(null)
    const historyList = ref([])

    function setCurrentPlan(plan) {
        currentPlan.value = plan
    }

    function setHistory(list) {
        historyList.value = list
    }

    function clearCurrentPlan() {
        currentPlan.value = null
    }

    return { currentPlan, historyList, setCurrentPlan, setHistory, clearCurrentPlan }
})