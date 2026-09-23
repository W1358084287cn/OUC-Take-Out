//获取所有的菜品分类
function categoryListApi() {
    return $axios({
        'url': '/category/list',
        'method': 'get',
    })
}

//获取菜品分类对应的菜品
function dishListApi(data) {
    return $axios({
        'url': '/dish/list',
        'method': 'get',
        params: {...data}
    })
}

//获取菜品分类对应的套餐
function setmealListApi(data) {
    return $axios({
        'url': '/setmeal/list',
        'method': 'get',
        params: {...data}
    })
}

//获取购物车内商品的集合
function cartListApi(data) {
    return $axios({
        'url': '/shoppingCart/list',
        'method': 'get',
        params: {...data}
    })
}

//购物车中添加商品
function addCartApi(data, tableId) {
    const params = tableId ? { tableId } : {}
    return $axios({
        'url': '/shoppingCart/add',
        'method': 'post',
        data,
        params
    })
}

//购物车中修改商品
function updateCartApi(data, tableId) {
    const params = tableId ? { tableId } : {}
    return $axios({
        'url': '/shoppingCart/sub',
        'method': 'post',
        data,
        params
    })
}

//删除购物车的商品
function clearCartApi(tableId) {
    const params = tableId ? { tableId } : {}
    return $axios({
        'url': '/shoppingCart/clean',
        'method': 'delete',
        params
    })
}

//获取套餐的全部菜品
function setMealDishDetailsApi(id) {
    return $axios({
        'url': `/setmeal/dish/${id}`,
        'method': 'get',
    })
}

//获取生效公告（C端首页公告滚条）
function announcementActiveApi() {
    return $axios({
        'url': '/announcement/active',
        'method': 'get',
    })
}

//查询门店营业状态
function businessStatusApi() {
    return $axios({
        'url': '/business/status',
        'method': 'get',
    })
}