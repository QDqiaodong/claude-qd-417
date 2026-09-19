import { createRouter, createWebHistory } from 'vue-router'
import IceLane from '../views/IceLane.vue'
import Member from '../views/Member.vue'
import Course from '../views/Course.vue'
import Enrollment from '../views/Enrollment.vue'

const routes = [
  { path: '/', redirect: '/ice-lanes' },
  { path: '/ice-lanes', name: '冰面', component: IceLane },
  { path: '/members', name: '会员', component: Member },
  { path: '/courses', name: '课程', component: Course },
  { path: '/enrollments', name: '选课', component: Enrollment }
]

export default createRouter({ history: createWebHistory(), routes })
