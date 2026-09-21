// 查询列表页接口
const getOrderDetailPage = (params) => {
  return $axios({
    url: '/order/page',
    method: 'get',
    params
  })
}

// 查看接口
const queryOrderDetailById = (id) => {
  return $axios({
    url: `/orderDetail/${id}`,
    method: 'get'
  })
}

// 取消，完成接口
const editOrderDetail = (params) => {
  return $axios({
    url: '/order',
    method: 'put',
    data: { ...params }
  })
}

// 退款接口
const refundOrder = (params) => {
  return $axios({
    url: '/order/refund',
    method: 'post',
    data: { ...params }
  })
}

// 批量删除订单
const batchDeleteOrders = (ids) => {
  return $axios({
    url: '/order/batch',
    method: 'delete',
    data: { ids }
  })
}

// 删除单个订单
const deleteOrderById = (id) => {
  return $axios({
    url: `/order/${id}`,
    method: 'delete'
  })
}

// 清理历史订单
const cleanOldOrders = (days) => {
  return $axios({
    url: '/order/clean',
    method: 'post',
    data: { days }
  })
}

// 获取订单清理配置（保留天数）
const getOrderRetentionDays = () => {
  return $axios({
    url: '/order/retention-days',
    method: 'get'
  })
}