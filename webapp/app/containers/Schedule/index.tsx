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

import React, { useMemo, useCallback, useEffect } from 'react'
import Helmet from 'react-helmet'
import { connect } from 'react-redux'
import { compose } from 'redux'
import { Link, RouteComponentProps } from 'react-router'
import injectReducer from 'utils/injectReducer'
import injectSaga from 'utils/injectSaga'
import { createStructuredSelector } from 'reselect'
import { makeSelectCurrentProject } from 'containers/Projects/selectors'
import { makeSelectLoading, makeSelectSchedules } from './selectors'
import { ScheduleActions } from './actions'
import reducer from './reducer'
import saga from './sagas'

import ModulePermission from 'containers/Account/components/checkModulePermission'
import { initializePermission } from 'containers/Account/components/checkUtilPermission'

import { useTablePagination } from 'utils/hooks'

import {
  Row,
  Col,
  Breadcrumb,
  Table,
  Icon,
  Button,
  Tooltip,
  Popconfirm
} from 'antd'
import { ButtonProps } from 'antd/lib/button'
import { ColumnProps } from 'antd/lib/table'
import Container from 'components/Container'
import Box from 'components/Box'

import { ISchedule, JobStatus, IScheduleLoading } from './types'
import { IRouteParams } from 'app/routes'
import { IProject } from 'containers/Projects'

import utilStyles from 'assets/less/util.less'
import Styles from './Schedule.less'

interface IScheduleListStateProps {
  loading: IScheduleLoading
  schedules: ISchedule[]
  currentProject: IProject
}

interface IScheduleListDispatchProps {
  onLoadSchedules: (projectId: number) => any
  onDeleteSchedule: (id: number) => any
  onChangeScheduleJobStatus: (id: number, status: JobStatus) => any
}

type ScheduleListProps = IScheduleListStateProps &
  IScheduleListDispatchProps &
  RouteComponentProps<{}, IRouteParams>

const JobStatusNextOperations: { [key in JobStatus]: string } = {
  new: '??????',
  failed: '??????',
  started: '??????',
  stopped: '??????'
}

const JobStatusIcons: { [key in JobStatus]: string } = {
  new: 'caret-right',
  failed: 'reload',
  started: 'pause',
  stopped: 'caret-right'
}

const columns: Array<ColumnProps<ISchedule>> = [
  {
    title: '??????',
    dataIndex: 'name',
    render: (name, record) => {
      if (!record.execLog) {
        return name
      }
      return (
        <>
          <span>{name}</span>
          <Tooltip title={record.execLog}>
            <Icon className={Styles.info} type="info-circle" />
          </Tooltip>
        </>
      )
    }
  },
  {
    title: '??????',
    dataIndex: 'description'
  },
  {
    title: '??????',
    dataIndex: 'jobType',
    width: 60,
    align: 'center'
  },
  {
    title: '??????????????????',
    dataIndex: 'startDate',
    width: 160
  },
  {
    title: '??????????????????',
    dataIndex: 'endDate',
    width: 160
  },
  {
    title: '??????',
    dataIndex: 'jobStatus',
    width: 70,
    align: 'center'
  }
]

const ScheduleList: React.FC<ScheduleListProps> = (props) => {
  const {
    params,
    router,
    loading,
    schedules,
    currentProject,
    onLoadSchedules,
    onDeleteSchedule,
    onChangeScheduleJobStatus
  } = props
  const tablePagination = useTablePagination(0)

  useEffect(() => {
    onLoadSchedules(+params.pid)
  }, [])

  const addSchedule = useCallback(
    () => {
      const { pid } = params
      router.push(`/project/${pid}/schedule`)
    },
    [currentProject]
  )

  const { schedulePermission, AdminButton, EditButton } = useMemo(
    () => ({
      schedulePermission: initializePermission(
        currentProject,
        'schedulePermission'
      ),
      AdminButton: ModulePermission<ButtonProps>(
        currentProject,
        'schedule',
        true
      )(Button),
      EditButton: ModulePermission<ButtonProps>(
        currentProject,
        'schedule',
        false
      )(Button)
    }),
    [currentProject]
  )

  const changeJobStatus = useCallback(
    (schedule: ISchedule) => () => {
      const { id, jobStatus } = schedule
      onChangeScheduleJobStatus(id, jobStatus)
    },
    [onChangeScheduleJobStatus]
  )

  const editSchedule = useCallback(
    (scheduleId: number) => () => {
      const { pid } = params
      router.push(`/project/${pid}/schedule/${scheduleId}`)
    },
    []
  )

  const deleteSchedule = useCallback(
    (scheduleId: number) => () => {
      onDeleteSchedule(scheduleId)
    },
    [onDeleteSchedule]
  )

  const tableColumns = [...columns]
  if (schedulePermission) {
    tableColumns.push({
      title: '??????',
      key: 'action',
      align: 'center',
      width: 145,
      render: (_, record) => (
        <span className="ant-table-action-column">
          <Tooltip title={JobStatusNextOperations[record.jobStatus]}>
            <Button
              icon={JobStatusIcons[record.jobStatus]}
              shape="circle"
              type="ghost"
              onClick={changeJobStatus(record)}
            />
          </Tooltip>
          <Tooltip title="??????" trigger="hover">
            <EditButton
              icon="edit"
              shape="circle"
              type="ghost"
              onClick={editSchedule(record.id)}
            />
          </Tooltip>
          <Popconfirm
            title="???????????????"
            placement="bottom"
            onConfirm={deleteSchedule(record.id)}
          >
            <Tooltip title="??????">
              <AdminButton icon="delete" shape="circle" type="ghost" />
            </Tooltip>
          </Popconfirm>
        </span>
      )
    })
  }

  return (
    <Container>
      <Helmet title="Schedule" />
      <Container.Title>
        <Row>
          <Col span={24}>
            <Breadcrumb className={utilStyles.breadcrumb}>
              <Breadcrumb.Item>
                <Link to="">Schedule</Link>
              </Breadcrumb.Item>
            </Breadcrumb>
          </Col>
        </Row>
      </Container.Title>
      <Container.Body>
        <Box>
          <Box.Header>
            <Box.Title>
              <Icon type="bars" />
              Schedule List
            </Box.Title>
            <Box.Tools>
              <Tooltip placement="bottom" title="??????">
                <AdminButton type="primary" icon="plus" onClick={addSchedule} />
              </Tooltip>
            </Box.Tools>
          </Box.Header>
          <Box.Body>
            <Row>
              <Col span={24}>
                <Table
                  rowKey="id"
                  bordered
                  dataSource={schedules}
                  columns={tableColumns}
                  pagination={tablePagination}
                  loading={loading.table}
                />
              </Col>
            </Row>
          </Box.Body>
        </Box>
      </Container.Body>
    </Container>
  )
}

const mapStateToProps = createStructuredSelector({
  schedules: makeSelectSchedules(),
  currentProject: makeSelectCurrentProject(),
  loading: makeSelectLoading()
})

function mapDispatchToProps(dispatch) {
  return {
    onLoadSchedules: (projectId) =>
      dispatch(ScheduleActions.loadSchedules(projectId)),
    onDeleteSchedule: (id) => dispatch(ScheduleActions.deleteSchedule(id)),
    onChangeScheduleJobStatus: (id, currentStatus) =>
      dispatch(ScheduleActions.changeSchedulesStatus(id, currentStatus))
  }
}

const withConnect = connect(
  mapStateToProps,
  mapDispatchToProps
)

const withReducer = injectReducer({ key: 'schedule', reducer })
const withSaga = injectSaga({ key: 'schedule', saga })

export default compose(
  withReducer,
  withSaga,
  withConnect
)(ScheduleList)
