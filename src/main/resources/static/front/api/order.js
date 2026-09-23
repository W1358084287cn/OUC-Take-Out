//提交订单
function  addOrderApi(data){
    return $axios({
        'url': '/order/submit',
        'method': 'post',
        data
      })
}

//支付订单
function payOrderApi(data){
    return $axios({
        'url': '/order/pay',
        'method': 'post',
        data
    })
}

//查询所有订单
function orderListApi() {
  return $axios({
    'url': '/order/list',
    'method': 'get',
  })
}

//分页查询订单
function orderPagingApi(data) {
  return $axios({
      'url': '/order/userPage',
      'method': 'get',
      params:{...data}
  })
}

//再来一单
function orderAgainApi(data) {
  return $axios({
      'url': '/order/again',
      'method': 'post',
      data
  })
}

//加餐
function addItemsApi(data) {
  return $axios({
      'url': '/order/addItems',
      'method': 'post',
      data
  })
}

//申请退款
function requestRefundApi(data) {
  return $axios({
      'url': '/order/requestRefund',
      'method': 'post',
      data
  })
}

//查询退款进度
function getRefundStatusApi(orderId) {
  return $axios({
      'url': `/order/refundStatus/${orderId}`,
      'method': 'get'
  })
}

//批量删除订单
function batchDeleteOrderApi(data) {
  return $axios({
      'url': '/order/batch',
      'method': 'delete',
      data
  })
}

//删除单个订单
function deleteOrderApi(id) {
  return $axios({
      'url': `/order/${id}`,
      'method': 'delete'
  })
}