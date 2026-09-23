// 获取营业配置
function getBusinessConfigApi() {
  return $axios({
    url: '/business/config',
    method: 'get'
  })
}

// 更新营业配置
function updateBusinessConfigApi(data) {
  return $axios({
    url: '/business/config',
    method: 'put',
    data: data
  })
}

// 一键开关门店
function toggleBusinessApi(open) {
  return $axios({
    url: '/business/toggle',
    method: 'put',
    data: { open: open }
  })
}