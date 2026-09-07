import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/zh-cn";

dayjs.extend(relativeTime);
dayjs.locale("zh-cn");

export default dayjs;

export const FORMAT_DATE = "YYYY-MM-DD";
export const FORMAT_DATETIME = "YYYY-MM-DD HH:mm:ss";
export const FORMAT_TIME = "HH:mm:ss";