/**
 * 收银台前端API
 */
function getCashierTodayApi() {
    return $axios({
        url: '/cashier/today',
        method: 'get'
    })
}

function getCashierHistoryApi(params) {
    return $axios({
        url: '/cashier/history',
        method: 'get',
        params: params
    })
}

function getCashierConfigApi() {
    return $axios({
        url: '/cashier/config',
        method: 'get'
    })
}

function updateCashierConfigApi(data) {
    return $axios({
        url: '/cashier/config',
        method: 'put',
        data: data
    })
}