/*
 * <<
 * Davinci
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

import React, { useEffect, useState, useCallback } from 'react'
import moment, { Moment } from 'moment'
import Helmet from 'react-helmet'
import { connect } from 'react-redux'
import { compose } from 'redux'
import { RouteComponentProps } from 'react-router'
import injectReducer from 'utils/injectReducer'
import injectSaga from 'utils/injectSaga'
import { createStructuredSelector } from 'reselect'
import {
  makeSelectLoading,
  makeSelectEditingSchedule,
  makeSelectSuggestMails,
  makeSelectPortalDashboards
} from './selectors'
import { makeSelectDisplays } from 'containers/Display/selectors'
import { makeSelectPortals } from 'containers/Portal/selectors'
import { checkNameUniqueAction } from 'containers/App/actions'
import { ScheduleActions } from './actions'
import { hideNavigator } from 'containers/App/actions'
import { DisplayActions } from 'containers/Display/actions'
import { loadPortals } from 'containers/Portal/actions'
import { loadDashboards } from 'containers/Dashboard/actions'
import reducer from './reducer'
import saga from './sagas'
import displayReducer from 'containers/Display/reducer'
import displaySaga from 'containers/Display/sagas'
import portalReducer from 'containers/Portal/reducer'
import portalSaga from 'containers/Portal/sagas'
import dashboardSaga from 'containers/Dashboard/sagas'

import { Row, Col, Card, Button, Icon, Tooltip, message } from 'antd'
import { FormComponentProps } from 'antd/lib/form/Form'

import ScheduleBaseConfig, {
  ScheduleBaseFormProps
} from './components/ScheduleBaseConfig'
import ScheduleMailConfig from './components/ScheduleMailConfig'
import ScheduleVizConfig from './components/ScheduleVizConfig'
import { IRouteParams } from 'app/routes'
import { IDashboard } from 'containers/Dashboard'
import { IDisplay } from 'containers/Display/types'
import { IPortal } from 'containers/Portal'
import { IProject } from 'containers/Projects'
import { ISchedule, IScheduleLoading } from './types'
import {
  IUserInfo,
  IScheduleMailConfig,
  SchedulePeriodUnit,
  ICronExpressionPartition
} from './components/types'

import Styles from './Schedule.less'
import StylesHeader from 'components/EditorHeader/EditorHeader.less'

const getCronExpressionByPartition = (partition: ICronExpressionPartition) => {
  const { periodUnit, minute, hour, day, weekDay, month } = partition
  let cronExpression = ''
  switch (periodUnit as SchedulePeriodUnit) {
    case 'Minute':
      cronExpression = `0 */${minute} * * * ?`
      break
    case 'Hour':
      cronExpression = `0 ${minute} * * * ?`
      break
    case 'Day':
      cronExpression = `0 ${minute} ${hour} * * ?`
      break
    case 'Week':
      cronExpression = `0 ${minute} ${hour} ? * ${weekDay}`
      break
    case 'Month':
      cronExpression = `0 ${minute} ${hour} ${day} * ?`
      break
    case 'Year':
      cronExpression = `0 ${minute} ${hour} ${day} ${month} ?`
      break
  }
  return cronExpression
}

interface IScheduleEditorStateProps {
  displays: IDisplay[]
  portals: IPortal[]
  portalDashboards: { [key: number]: IDashboard[] }
  loading: IScheduleLoading
  editingSchedule: ISchedule
  suggestMails: IUserInfo[]
  currentProject: IProject
}

interface IScheduleEditorDispatchProps {
  onHideNavigator: () => void
  onLoadDisplays: (projectId: number) => void
  onLoadPortals: (projectId: number) => void
  onLoadDashboards: (portalId: number) => void
  onLoadScheduleDetail: (scheduleId: number) => void
  onAddSchedule: (schedule: ISchedule, resolve: () => void) => any
  onEditSchedule: (schedule: ISchedule, resolve: () => void) => any
  onResetState: () => void
  onCheckUniqueName: (
    data: any,
    resolve: () => any,
    reject: (error: string) => any
  ) => any
  onLoadSuggestMails: (keyword: string) => any
}

type ScheduleEditorProps = IScheduleEditorStateProps &
  IScheduleEditorDispatchProps &
  RouteComponentProps<{}, IRouteParams>

const ScheduleEditor: React.FC<ScheduleEditorProps> = (props) => {
  const {
    onHideNavigator,
    onLoadDisplays,
    onLoadPortals,
    onLoadScheduleDetail,
    onResetState,
    params,
    router
  } = props
  const { pid: projectId, scheduleId } = params
  useEffect(() => {
    onHideNavigator()
    onLoadDisplays(+projectId)
    onLoadPortals(+projectId)
    if (+scheduleId) {
      onLoadScheduleDetail(+scheduleId)
    }

    return () => {
      onResetState()
    }
  }, [])
  const goBack = useCallback(() => {
    router.push(`/project/${projectId}/schedules`)
  }, [])

  const { portals, loading, editingSchedule, onLoadDashboards } = props

  useEffect(
    () => {
      if (!editingSchedule.id || !Array.isArray(portals)) {
        return
      }
      const { contentList } = editingSchedule.config
      // initial Dashboards loading by contentList Portal setting
      contentList
        .filter(({ contentType }) => contentType === 'portal')
        .forEach(({ id }) => {
          if (~portals.findIndex((portal) => portal.id === id)) {
            onLoadDashboards(id)
          }
        })
    },
    [portals, editingSchedule]
  )

  const {
    displays,
    suggestMails,
    portalDashboards,
    onAddSchedule,
    onEditSchedule,
    onCheckUniqueName,
    onLoadSuggestMails
  } = props
  const { jobStatus, config } = editingSchedule
  const { contentList } = config

  const [localContentList, setLocalContentList] = useState(contentList)
  useEffect(
    () => {
      setLocalContentList([...contentList])
    },
    [contentList]
  )

  let baseConfigForm: FormComponentProps<ScheduleBaseFormProps> = null
  let mailConfigForm: FormComponentProps<IScheduleMailConfig> = null

  const saveSchedule = () => {
    if (!localContentList.length) {
      message.error('?????????????????????')
      return
    }
    baseConfigForm.form.validateFieldsAndScroll((err1, value1) => {
      if (err1) {
        return
      }
      const cronExpression = getCronExpressionByPartition(value1)
      const [startDate, endDate] = baseConfigForm.form.getFieldValue(
        'dateRange'
      ) as ScheduleBaseFormProps['dateRange']
      delete value1.dateRange
      mailConfigForm.form.validateFieldsAndScroll((err2, value2) => {
        if (err2) {
          return
        }
        const schedule: ISchedule = {
          ...value1,
          cronExpression,
          startDate: moment(startDate).format('YYYY-MM-DD HH:mm:ss'),
          endDate: moment(endDate).format('YYYY-MM-DD HH:mm:ss'),
          config: { ...value2, contentList: localContentList },
          projectId: +projectId
        }
        if (editingSchedule.id) {
          schedule.id = editingSchedule.id
          onEditSchedule(schedule, goBack)
        } else {
          onAddSchedule(schedule, goBack)
        }
      })
    })
  }

  return (
    <>
      <Helmet title="Schedule" />
      <div className={Styles.scheduleEditor}>
        <div className={StylesHeader.editorHeader}>
          <Icon type="left" className={StylesHeader.back} onClick={goBack} />
          <div className={StylesHeader.title}>
            <span className={StylesHeader.name}>{`${
              scheduleId ? '??????' : '??????'
            } Schedule`}</span>
          </div>
          <div className={StylesHeader.actions}>
            <Tooltip
              placement="bottom"
              title={jobStatus === 'started' ? '?????????????????????' : ''}
            >
              <Button
                type="primary"
                disabled={loading.edit || jobStatus === 'started'}
                onClick={saveSchedule}
              >
                ??????
              </Button>
            </Tooltip>
          </div>
        </div>
        <div className={Styles.containerVertical}>
          <Row gutter={8}>
            <Col span={12}>
              <Card title="????????????" size="small">
                <ScheduleBaseConfig
                  wrappedComponentRef={(inst) => {
                    baseConfigForm = inst
                  }}
                  schedule={editingSchedule}
                  loading={loading.schedule}
                  onCheckUniqueName={onCheckUniqueName}
                />
              </Card>
              <Card title="????????????" size="small" style={{ marginTop: 8 }}>
                <ScheduleMailConfig
                  wrappedComponentRef={(inst) => {
                    mailConfigForm = inst
                  }}
                  config={config}
                  loading={loading.schedule}
                  mailList={suggestMails}
                  onLoadMailList={onLoadSuggestMails}
                />
              </Card>
            </Col>
            <Col span={12}>
              <Card title="??????????????????" size="small">
                <ScheduleVizConfig
                  displays={displays}
                  portals={portals}
                  portalDashboards={portalDashboards}
                  value={localContentList}
                  onLoadPortalDashboards={onLoadDashboards}
                  onChange={setLocalContentList}
                />
              </Card>
            </Col>
          </Row>
        </div>
      </div>
    </>
  )
}

const mapStateToProps = createStructuredSelector({
  displays: makeSelectDisplays(),
  portals: makeSelectPortals(),
  portalDashboards: makeSelectPortalDashboards(),
  loading: makeSelectLoading(),
  editingSchedule: makeSelectEditingSchedule(),
  suggestMails: makeSelectSuggestMails()
})

const mapDispatchToProps = (dispatch) => ({
  onHideNavigator: () => dispatch(hideNavigator()),
  onLoadDisplays: (projectId) =>
    dispatch(DisplayActions.loadDisplays(projectId)),
  onLoadPortals: (projectId) => dispatch(loadPortals(projectId)),
  onLoadDashboards: (portalId) =>
    dispatch(
      loadDashboards(portalId, (dashboards) => {
        dispatch(ScheduleActions.portalDashboardsLoaded(portalId, dashboards))
      })
    ),
  onLoadScheduleDetail: (scheduleId) =>
    dispatch(ScheduleActions.loadScheduleDetail(scheduleId)),
  onAddSchedule: (schedule, resolve) =>
    dispatch(ScheduleActions.addSchedule(schedule, resolve)),
  onEditSchedule: (schedule, resolve) =>
    dispatch(ScheduleActions.editSchedule(schedule, resolve)),
  onResetState: () => dispatch(ScheduleActions.resetScheduleState()),
  onCheckUniqueName: (data, resolve, reject) =>
    dispatch(checkNameUniqueAction('cronjob', data, resolve, reject)),
  onLoadSuggestMails: (keyword) =>
    dispatch(ScheduleActions.loadSuggestMails(keyword))
})

const withConnect = connect(
  mapStateToProps,
  mapDispatchToProps
)
const withReducer = injectReducer({ key: 'schedule', reducer })
const withSaga = injectSaga({ key: 'schedule', saga })
const withDisplayReducer = injectReducer({
  key: 'display',
  reducer: displayReducer
})
const withDisplaySaga = injectSaga({ key: 'display', saga: displaySaga })
const withPortalReducer = injectReducer({
  key: 'portal',
  reducer: portalReducer
})
const withPortalSaga = injectSaga({ key: 'portal', saga: portalSaga })
const withDashboardSaga = injectSaga({ key: 'dashboard', saga: dashboardSaga })

export default compose(
  withReducer,
  withSaga,
  withDisplayReducer,
  withDisplaySaga,
  withPortalReducer,
  withPortalSaga,
  withDashboardSaga,
  withConnect
)(ScheduleEditor)
