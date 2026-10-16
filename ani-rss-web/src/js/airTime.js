// airingAt 是排期源提供的下一集 Unix 时间戳（秒），不从首播日期推算。
export const formatNextAirTime = (anime, now = Date.now()) => {
  const pending = {hasTime: false, display: '放送时间待定', tooltip: '暂无下一集的准确放送排期'}
  const timestamp = Number(anime?.airingAt) * 1000
  if (!Number.isFinite(timestamp) || timestamp <= 0) return pending
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return pending
  if (timestamp <= now) {
    return {hasTime: false, display: '排期待更新', tooltip: '现有排期已过，等待下一集的准确放送时间'}
  }
  const partsIn = (timeZone) => {
    const parts = new Intl.DateTimeFormat('zh-CN', {
      timeZone, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
    }).formatToParts(date)
    const get = type => parts.find(part => part.type === type)?.value || ''
    return `${get('month')}-${get('day')} ${get('hour')}:${get('minute')}`
  }
  const beijing = partsIn('Asia/Shanghai')
  return {
    hasTime: true,
    display: beijing,
    tooltip: `下一集预定放送：北京时间 ${beijing}；日本时间 ${partsIn('Asia/Tokyo')} (JST)。排期来源：AniBT；实际放送以官方为准。`
  }
}
