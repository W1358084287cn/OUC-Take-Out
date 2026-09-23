// 获取所有公告列表（含已下架）
function getAnnouncementListApi() {
  return $axios({
    url: '/announcement/list',
    method: 'get'
  })
}

// 新增公告
function addAnnouncementApi(data) {
  return $axios({
    url: '/announcement',
    method: 'post',
    data: data
  })
}

// 编辑公告
function updateAnnouncementApi(data) {
  return $axios({
    url: '/announcement',
    method: 'put',
    data: data
  })
}

// 下架公告
function removeAnnouncementApi(id) {
  return $axios({
    url: '/announcement/' + id,
    method: 'delete'
  })
}